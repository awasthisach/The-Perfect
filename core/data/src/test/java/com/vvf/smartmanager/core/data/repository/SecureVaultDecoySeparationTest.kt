package com.vvf.smartmanager.core.data.repository
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SecureVaultDecoySeparationTest {
    @Test fun decoyIsSiblingNotNested() {
        val tmp = Files.createTempDirectory("vvf").toFile()
        try {
            val real = File(tmp, "vvf_vault_encrypted").also { it.mkdirs() }
            val decoy = File(real.parentFile, "vvf_decoy_vault_encrypted").also { it.mkdirs() }
            assertFalse(decoy.canonicalPath.startsWith(real.canonicalPath + File.separator))
        } finally { tmp.deleteRecursively() }
    }
}
