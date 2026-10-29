package dev.lfm.mobile.inference

import org.junit.Assert.*
import org.junit.Test

class ImportSelectionTest {
    @Test fun wrongSlotIdentifiesTheSelectedFile() {
        val error = ImportSelection.problem(VoicePart.PROJECTOR.filename, VoicePart.PROJECTOR.bytes,
            VoicePart.MODEL.filename, VoicePart.MODEL.bytes)
        assertTrue(error!!.contains(VoicePart.PROJECTOR.filename))
        assertTrue(error.contains(VoicePart.MODEL.filename))
    }
    @Test fun incompleteDownloadShowsObservedAndExpectedSizes() {
        val error = ImportSelection.problem("renamed.gguf", 123L, ModelStore.FILENAME, ModelStore.SIZE)
        assertTrue(error!!.contains("123 bytes"))
        assertTrue(error.contains(ModelStore.SIZE.toString()))
    }
    @Test fun renamedMatchingSizeAndUnknownMetadataStillRequireStreamVerification() {
        assertNull(ImportSelection.problem("my-model.gguf", ModelStore.SIZE, ModelStore.FILENAME, ModelStore.SIZE))
        assertNull(ImportSelection.problem(null, null, ModelStore.FILENAME, ModelStore.SIZE))
        assertNull(ImportSelection.problem(null, -1L, ModelStore.FILENAME, ModelStore.SIZE))
    }
}
