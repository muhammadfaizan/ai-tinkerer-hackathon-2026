package io.github.muhammadfaizan.intune

import android.content.pm.ApplicationInfo
import io.github.muhammadfaizan.intune.tracking.readableCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageCategoryTest {
    @Test fun mapsKnownAndUnknownApplicationCategories() {
        assertEquals("Social", readableCategory(ApplicationInfo.CATEGORY_SOCIAL))
        assertNull(readableCategory(99))
    }
}
