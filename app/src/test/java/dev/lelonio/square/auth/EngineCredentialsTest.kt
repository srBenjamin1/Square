package dev.lelonio.square.auth

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineCredentialsTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun interruptedUpdateDoesNotTreatEmptyOrBrokenCredentialAsLoggedIn() {
        val file = temp.root.resolve("credentials.json")
        assertFalse(EngineCredentials.isReadable(file))
        file.writeText("")
        assertFalse(EngineCredentials.isReadable(file))
        file.writeText("{broken")
        assertFalse(EngineCredentials.isReadable(file))
        file.writeText("{\"username\":\"test\",\"auth_type\":1,\"auth_data\":[]}")
        assertTrue(EngineCredentials.isReadable(file))
    }
}
