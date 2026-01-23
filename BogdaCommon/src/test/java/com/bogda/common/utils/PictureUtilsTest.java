package com.bogda.common.utils;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;

public class PictureUtilsTest {

    @Test
    void testAidgeImageTranslateInputCodeSet() {
        assertTrue(PictureUtils.aidgeImageTranslateInputCodeSet.contains("zh"));
        assertTrue(PictureUtils.aidgeImageTranslateInputCodeSet.contains("en"));
        assertTrue(PictureUtils.aidgeImageTranslateInputCodeSet.contains("ja"));
        assertFalse(PictureUtils.aidgeImageTranslateInputCodeSet.contains("xx"));
    }

    @Test
    void testAidgeImageTranslateOutputCodeSet() {
        assertTrue(PictureUtils.aidgeImageTranslateOutputCodeSet.contains("en"));
        assertTrue(PictureUtils.aidgeImageTranslateOutputCodeSet.contains("zh"));
        assertTrue(PictureUtils.aidgeImageTranslateOutputCodeSet.contains("ar"));
        assertFalse(PictureUtils.aidgeImageTranslateOutputCodeSet.contains("xx"));
    }

    @Test
    void testIsBaseImageTranslateInputCode() {
        assertTrue(PictureUtils.isBaseImageTranslateInputCode("en", "zh-tw"));
        assertTrue(PictureUtils.isBaseImageTranslateInputCode("zh", "zh-tw"));
        assertTrue(PictureUtils.isBaseImageTranslateInputCode("tr", "el"));
        assertTrue(PictureUtils.isBaseImageTranslateInputCode("en", "el"));
        assertTrue(PictureUtils.isBaseImageTranslateInputCode("zh", "kk"));
        assertTrue(PictureUtils.isBaseImageTranslateInputCode("en", "en"));
    }

    @Test
    void testIsBaseImageTranslateInputCodeWithInvalid() {
        assertFalse(PictureUtils.isBaseImageTranslateInputCode("fr", "zh-tw"));
        assertFalse(PictureUtils.isBaseImageTranslateInputCode("en", "kk"));
    }

    @Test
    void testIsDifferentImageTranslateInputCodeWithModel1() {
        assertTrue(PictureUtils.isDifferentImageTranslateInputCode("zh", "en", 1));
        assertTrue(PictureUtils.isDifferentImageTranslateInputCode("en", "zh", 1));
        assertFalse(PictureUtils.isDifferentImageTranslateInputCode("xx", "en", 1));
        assertFalse(PictureUtils.isDifferentImageTranslateInputCode("en", "xx", 1));
    }

    @Test
    void testIsDifferentImageTranslateInputCodeWithModel2() {
        assertTrue(PictureUtils.isDifferentImageTranslateInputCode("en", "zh", 2));
        assertTrue(PictureUtils.isDifferentImageTranslateInputCode("zh", "en", 2));
        assertFalse(PictureUtils.isDifferentImageTranslateInputCode("xx", "en", 2));
    }

    @Test
    void testIsDifferentImageTranslateInputCodeWithInvalidModel() {
        assertFalse(PictureUtils.isDifferentImageTranslateInputCode("en", "zh", 3));
        assertFalse(PictureUtils.isDifferentImageTranslateInputCode("en", "zh", 0));
    }

    @Test
    void testHuoShanImageType() {
        assertTrue(PictureUtils.HUO_SHAN_IMAGE_TYPE.contains("png"));
        assertTrue(PictureUtils.HUO_SHAN_IMAGE_TYPE.contains("jpg"));
        assertFalse(PictureUtils.HUO_SHAN_IMAGE_TYPE.contains("jpeg"));
    }

    @Test
    void testAigdeImageType() {
        assertTrue(PictureUtils.AIGDE_IMAGE_TYPE.contains("png"));
        assertTrue(PictureUtils.AIGDE_IMAGE_TYPE.contains("jpg"));
        assertTrue(PictureUtils.AIGDE_IMAGE_TYPE.contains("jpeg"));
        assertTrue(PictureUtils.AIGDE_IMAGE_TYPE.contains("bmp"));
        assertTrue(PictureUtils.AIGDE_IMAGE_TYPE.contains("webp"));
        assertFalse(PictureUtils.AIGDE_IMAGE_TYPE.contains("gif"));
    }

    @Test
    void testIsSupportModelAndImageType() {
        assertTrue(PictureUtils.isSupportModelAndImageType("png", 1));
        assertTrue(PictureUtils.isSupportModelAndImageType("jpg", 1));
        assertTrue(PictureUtils.isSupportModelAndImageType("jpeg", 1));
        assertTrue(PictureUtils.isSupportModelAndImageType("png", 2));
        assertTrue(PictureUtils.isSupportModelAndImageType("jpg", 2));
        assertFalse(PictureUtils.isSupportModelAndImageType("gif", 1));
        assertFalse(PictureUtils.isSupportModelAndImageType("jpeg", 2));
        assertFalse(PictureUtils.isSupportModelAndImageType("png", 3));
    }

    @Test
    void testGetExtensionFromUrl() {
        assertEquals("jpg", PictureUtils.getExtensionFromUrl("https://example.com/image.jpg"));
        assertEquals("png", PictureUtils.getExtensionFromUrl("https://example.com/image.png"));
        assertEquals("jpg", PictureUtils.getExtensionFromUrl("https://example.com/image.jpg?v=123"));
        assertEquals("png", PictureUtils.getExtensionFromUrl("image.png?param=value"));
    }

    @Test
    void testGetExtensionFromUrlWithNoExtension() {
        // URL 中没有任何点的情况
        assertNull(PictureUtils.getExtensionFromUrl("image"));
        assertNull(PictureUtils.getExtensionFromUrl("/path/to/file"));
        // URL 中最后一个点后面没有内容的情况
        assertNull(PictureUtils.getExtensionFromUrl("https://example.com/image."));
        assertNull(PictureUtils.getExtensionFromUrl("file."));
    }

    @Test
    void testGetExtensionFromUrlWithNull() {
        assertThrows(NullPointerException.class, () -> {
            PictureUtils.getExtensionFromUrl(null);
        });
    }

    @Test
    void testGetMediaTypeByImageType() {
        assertEquals(MediaType.IMAGE_PNG, PictureUtils.getMediaTypeByImageType("png"));
        assertEquals(MediaType.IMAGE_JPEG, PictureUtils.getMediaTypeByImageType("jpg"));
        assertEquals(MediaType.IMAGE_JPEG, PictureUtils.getMediaTypeByImageType("jpeg"));
        assertNotNull(PictureUtils.getMediaTypeByImageType("webp"));
        assertNotNull(PictureUtils.getMediaTypeByImageType("heic"));
        assertNotNull(PictureUtils.getMediaTypeByImageType("heif"));
    }

    @Test
    void testGetMediaTypeByImageTypeWithInvalid() {
        assertNull(PictureUtils.getMediaTypeByImageType("gif"));
        assertNull(PictureUtils.getMediaTypeByImageType("unknown"));
    }

    @Test
    void testImageMimeMap() {
        assertEquals("image/png", PictureUtils.IMAGE_MIME_MAP.get("png"));
        assertEquals("image/jpeg", PictureUtils.IMAGE_MIME_MAP.get("jpg"));
        assertEquals("image/jpeg", PictureUtils.IMAGE_MIME_MAP.get("jpeg"));
        assertEquals("image/webp", PictureUtils.IMAGE_MIME_MAP.get("webp"));
        assertEquals("image/heic", PictureUtils.IMAGE_MIME_MAP.get("heic"));
        assertEquals("image/heif", PictureUtils.IMAGE_MIME_MAP.get("heif"));
    }
}

