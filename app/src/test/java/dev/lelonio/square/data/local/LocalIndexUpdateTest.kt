package dev.lelonio.square.data.local

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LocalIndexUpdateTest {
    private fun row(id:Long)=LocalTrack(id,"content://media/external/audio/media/$id","Song",size=100,modifiedSeconds=1)
    @Test fun firstScanEnrichesEveryFileWithoutCopyingIt()=runBlocking {
        val reads=mutableListOf<Long>()
        val rows=listOf(row(1),row(2))
        val result=updateLocalIndex(emptyList(),rows,false,{ reads+=it.id;it.copy(genre="Rock") })
        assertEquals(listOf(1L,2L),reads)
        assertEquals(rows.map { it.contentUri },result.map { it.contentUri })
        assertTrue(result.all { it.genre=="Rock" })
    }
    @Test fun unchangedFilesReusePreviouslyReadTags()=runBlocking {
        val result=updateLocalIndex(listOf(row(1).copy(albumArtist="Album artist")),listOf(row(1)),false,{error("Should reuse metadata")})
        assertEquals("Album artist",result.single().albumArtist)
    }
    @Test fun onlyNewOrModifiedFilesAreOpened()=runBlocking {
        val reads=mutableListOf<Long>()
        val result=updateLocalIndex(listOf(row(1),row(2)),listOf(row(1),row(2).copy(size=101),row(3)),false,{reads+=it.id;it})
        assertEquals(listOf(2L,3L),reads)
        assertEquals(3,result.size)
    }
    @Test fun metadataAndGenerationChangesTriggerReindexEvenWithoutMtimeChanges()=runBlocking {
        var reads=0
        updateLocalIndex(listOf(row(1),row(2)),listOf(row(1).copy(generation=2),row(2).copy(rowSignature="changed-tags")),false,{reads++;it})
        assertEquals(2,reads)
    }
    @Test fun manualRescanReadsTagsAgain()=runBlocking {
        var reads=0
        updateLocalIndex(listOf(row(1)),listOf(row(1)),true,{reads++;it})
        assertEquals(1,reads)
    }
    @Test fun deletedFilesLeaveTheIndexAndRepeatedRowsCannotDuplicateThem()=runBlocking {
        val result=updateLocalIndex(listOf(row(1),row(2)),listOf(row(2),row(2)),false,{error("Unchanged")})
        assertEquals(listOf(2L),result.map { it.id })
    }
    @Test fun cancellationDoesNotReturnAPartialCollection()=runBlocking {
        var returned=false
        val job=launch {
            updateLocalIndex(emptyList(),listOf(row(1),row(2)),false,{
                if(it.id==2L) throw CancellationException()
                it
            })
            returned=true
        }
        job.join()
        assertFalse(returned)
    }
    @Test fun progressUsesUniqueFileCount()=runBlocking {
        val updates=mutableListOf<Pair<Int,Int>>()
        updateLocalIndex(emptyList(),listOf(row(1),row(1),row(2)),false,{it},{done,total->updates+=done to total})
        assertEquals(listOf(1 to 2,2 to 2),updates)
    }

    @Test fun anAuthorizedDocumentAliasReusesTagsButRefreshesTheCurrentAccessPath()=runBlocking {
        val media=row(1).copy(storageKey="primary|Music/one.flac")
        val document=media.copy(contentUri="content://provider/document/one", documentKey="provider|one",treeUris=listOf("tree"),albumArtist="Album artist")
        val result=updateLocalIndex(listOf(document),listOf(media),false,{error("same physical row must reuse its tags")}).single()
        assertEquals("Album artist",result.albumArtist)
        assertEquals(media.contentUri,result.contentUri)
        assertTrue(result.treeUris.isEmpty())
    }
}
