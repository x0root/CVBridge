package com.example

import org.junit.Test
import org.opencv.android.OpenCVLoader
import org.junit.Assert.assertTrue

class OpenCVTest {
    @Test
    fun testOpenCVInit() {
        val result = OpenCVLoader.initDebug()
        System.out.println("OpenCVInit result: " + result)
    }
}
