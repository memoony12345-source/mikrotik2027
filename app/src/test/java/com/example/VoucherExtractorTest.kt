package com.example

import com.example.data.model.VoucherCodeMode
import com.example.network.NetworkPortalMonitor
import com.example.ocr.OcrLineBox
import com.example.ocr.VoucherExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherExtractorTest {

    @Test
    fun `extracts 6 to 12 digit and alphanumeric codes while ignoring short and noise words`() {
        val lines = listOf(
            OcrLineBox("MIKROTIK HOTSPOT", null),
            OcrLineBox("PASSWORD", null),
            OcrLineBox("12345", null), // < 6 chars, must be ignored
            OcrLineBox("MK7X92P4", null), // 8 chars alphanumeric
            OcrLineBox("9876543210123", null), // 13 chars > 12, must be ignored
        )
        val result = VoucherExtractor.extractFromLines(lines, VoucherCodeMode.AUTO)
        assertEquals(1, result.size)
        assertEquals("MK7X92P4", result.first().code)
    }

    @Test
    fun `supports grouped codes and arabic digits normalization`() {
        val lines = listOf(
            OcrLineBox("4829 1038", null),
            OcrLineBox("٤٨٢٩١٠", null),
        )
        val result = VoucherExtractor.extractFromLines(lines, VoucherCodeMode.DIGITS_ONLY)
        val codes = result.map { it.code }
        assertTrue("48291038" in codes)
        assertTrue("482910" in codes)
    }

    @Test
    fun `digits only mode ignores alphanumeric tokens`() {
        val lines = listOf(
            OcrLineBox("AB123456", null),
            OcrLineBox("74185296", null),
        )
        val result = VoucherExtractor.extractFromLines(lines, VoucherCodeMode.DIGITS_ONLY)
        assertEquals(1, result.size)
        assertEquals("74185296", result.first().code)
    }

    @Test
    fun `extracts mikrotik meta refresh and js redirect urls`() {
        val metaHtml = """<html><head><meta http-equiv="refresh" content="0; url=http://10.5.50.1/login?dst=http://example.com"></head></html>"""
        assertEquals(
            "http://10.5.50.1/login?dst=http://example.com",
            NetworkPortalMonitor.extractHtmlRedirectUrl(metaHtml),
        )

        val jsHtml = """<script>window.location = 'http://192.168.88.1/login';</script>"""
        assertEquals(
            "http://192.168.88.1/login",
            NetworkPortalMonitor.extractHtmlRedirectUrl(jsHtml),
        )
    }
}
