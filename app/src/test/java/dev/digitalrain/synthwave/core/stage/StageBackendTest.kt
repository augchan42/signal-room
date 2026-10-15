package dev.digitalrain.synthwave.core.stage

import org.junit.Assert.assertEquals
import org.junit.Test

class StageBackendTest {
    @Test fun vulkanOnlyOnRecentGooglePhonesWithVulkan11() {
        assertEquals(StageBackend.VULKAN, chooseBackend("Google", 34, vulkan11 = true, override = ""))
        assertEquals(StageBackend.OPENGL, chooseBackend("Google", 30, vulkan11 = true, override = ""))
        assertEquals(StageBackend.OPENGL, chooseBackend("Google", 34, vulkan11 = false, override = ""))
        assertEquals(StageBackend.OPENGL, chooseBackend("samsung", 34, vulkan11 = true, override = ""))
    }

    @Test fun debugPropertyOverridesTheList() {
        assertEquals(StageBackend.OPENGL, chooseBackend("Google", 34, vulkan11 = true, override = "opengl"))
        assertEquals(StageBackend.VULKAN, chooseBackend("samsung", 34, vulkan11 = true, override = "vulkan"))
    }
}
