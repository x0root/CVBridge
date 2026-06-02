package com.example

import android.graphics.Bitmap
import io.ktor.http.Parameters
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.features2d.Features2d
import org.opencv.features2d.ORB
import org.opencv.features2d.SIFT
import org.opencv.objdetect.QRCodeDetector

private var cachedQRDetector: QRCodeDetector? = null
private var cachedORB: ORB? = null
private var cachedSIFT: SIFT? = null

fun processAdvancedOpenCV(bitmap: Bitmap, task: String, params: Parameters): Bitmap? {
    try {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        val dst = Mat()
        
        when (task.lowercase().replace("cv2.", "")) {
            "imread", "imwrite", "imshow" -> {
                src.copyTo(dst)
            }
            "resize" -> {
                val w = params["width"]?.toDoubleOrNull() ?: (src.cols().toDouble() / 2)
                val h = params["height"]?.toDoubleOrNull() ?: (src.rows().toDouble() / 2)
                Imgproc.resize(src, dst, Size(w, h))
            }
            "cvtcolor" -> {
                val codeStr = params["code"]?.uppercase() ?: "COLOR_RGBA2GRAY"
                val code = try { Imgproc::class.java.getField(codeStr).getInt(null) } catch (e: Exception) { Imgproc.COLOR_RGBA2GRAY }
                Imgproc.cvtColor(src, dst, code)
            }
            "flip" -> {
                val flipCode = params["flipCode"]?.toIntOrNull() ?: 1
                Core.flip(src, dst, flipCode)
            }
            "rotate" -> {
                val rotateCode = params["rotateCode"]?.toIntOrNull() ?: Core.ROTATE_90_CLOCKWISE
                Core.rotate(src, dst, rotateCode)
            }
            "threshold" -> {
                val thresh = params["thresh"]?.toDoubleOrNull() ?: 128.0
                val maxval = params["maxval"]?.toDoubleOrNull() ?: 255.0
                var type = Imgproc.THRESH_BINARY
                params["type"]?.let { t -> 
                     if (t.contains("INV", ignoreCase = true)) type = Imgproc.THRESH_BINARY_INV
                }
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.threshold(gray, dst, thresh, maxval, type)
                gray.release()
            }
            "adaptivethreshold" -> {
                val maxValue = params["maxValue"]?.toDoubleOrNull() ?: 255.0
                val method = Imgproc.ADAPTIVE_THRESH_MEAN_C
                val type = Imgproc.THRESH_BINARY
                var blockSize = params["blockSize"]?.toIntOrNull() ?: 11
                if (blockSize % 2 == 0) blockSize += 1
                if (blockSize < 3) blockSize = 3
                val c = params["c"]?.toDoubleOrNull() ?: 2.0
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.adaptiveThreshold(gray, dst, maxValue, method, type, blockSize, c)
                gray.release()
            }
            "gaussianblur" -> {
                var ksize = params["ksize"]?.toDoubleOrNull() ?: 5.0
                if (ksize.toInt() % 2 == 0) ksize += 1.0
                if (ksize < 1.0) ksize = 1.0
                Imgproc.GaussianBlur(src, dst, Size(ksize, ksize), params["sigmaX"]?.toDoubleOrNull() ?: 0.0)
            }
            "medianblur" -> {
                var ksize = params["ksize"]?.toIntOrNull() ?: 5
                if (ksize % 2 == 0) ksize += 1
                if (ksize < 1) ksize = 1
                Imgproc.medianBlur(src, dst, ksize)
            }
            "bilateralfilter" -> {
                val d = params["d"]?.toIntOrNull() ?: 9
                val sigmaColor = params["sigmaColor"]?.toDoubleOrNull() ?: 75.0
                val sigmaSpace = params["sigmaSpace"]?.toDoubleOrNull() ?: 75.0
                val rgb = Mat()
                Imgproc.cvtColor(src, rgb, Imgproc.COLOR_RGBA2RGB)
                Imgproc.bilateralFilter(rgb, dst, d, sigmaColor, sigmaSpace)
                rgb.release()
            }
            "blur" -> {
                var ksize = params["ksize"]?.toDoubleOrNull() ?: 5.0
                if (ksize < 1.0) ksize = 1.0
                Imgproc.blur(src, dst, Size(ksize, ksize))
            }
            "sobel" -> {
                val dx = params["dx"]?.toIntOrNull() ?: 1
                val dy = params["dy"]?.toIntOrNull() ?: 1
                var ksize = params["ksize"]?.toIntOrNull() ?: 3
                if (ksize % 2 == 0) ksize += 1
                if (ksize < 1) ksize = 1
                if (ksize > 7) ksize = 7
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.Sobel(gray, dst, CvType.CV_16S, dx, dy, ksize)
                Core.convertScaleAbs(dst, dst)
                gray.release()
            }
            "laplacian" -> {
                var ksize = params["ksize"]?.toIntOrNull() ?: 3
                if (ksize % 2 == 0) ksize += 1
                if (ksize < 1) ksize = 1
                if (ksize > 7) ksize = 7
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.Laplacian(gray, dst, CvType.CV_16S, ksize)
                Core.convertScaleAbs(dst, dst)
                gray.release()
            }
            "scharr" -> {
                val dx = params["dx"]?.toIntOrNull() ?: 1
                val dy = params["dy"]?.toIntOrNull() ?: 0
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                try {
                    Imgproc.Scharr(gray, dst, CvType.CV_16S, dx, dy)
                    Core.convertScaleAbs(dst, dst)
                } catch(e: Exception) {
                    src.copyTo(dst)
                }
                gray.release()
            }
            "erode", "dilate", "morphologyex", "getstructuringelement" -> {
                val iters = params["iterations"]?.toIntOrNull() ?: 1
                val ksize = params["ksize"]?.toDoubleOrNull() ?: 3.0
                val element = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(ksize, ksize))
                when (task.lowercase().replace("cv2.", "")) {
                    "erode" -> Imgproc.erode(src, dst, element, Point(-1.0, -1.0), iters)
                    "dilate" -> Imgproc.dilate(src, dst, element, Point(-1.0, -1.0), iters)
                    else -> {
                        val opStr = params["op"]?.uppercase() ?: "MORPH_OPEN"
                        val op = try { Imgproc::class.java.getField(opStr).getInt(null) } catch(e: Exception) { Imgproc.MORPH_OPEN }
                        Imgproc.morphologyEx(src, dst, op, element, Point(-1.0, -1.0), iters)
                    }
                }
                element.release()
            }
            "findcontours", "drawcontours", "contour", "contours", "contourarea", "boundingrect", "minarearect", "convexhull", "approxpolydp", "matchshapes" -> {
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.threshold(gray, gray, 128.0, 255.0, Imgproc.THRESH_BINARY)
                val contours = ArrayList<MatOfPoint>()
                val hierarchy = Mat()
                Imgproc.findContours(gray, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                src.copyTo(dst)
                Imgproc.drawContours(dst, contours, -1, Scalar(0.0, 255.0, 0.0, 255.0), 2)
                
                // Show boundingRects as a bonus for these advanced operations
                if (task.lowercase().contains("boundingrect") || task.lowercase().contains("minarearect")) {
                    for (contour in contours) {
                        val rect = Imgproc.boundingRect(contour)
                        Imgproc.rectangle(dst, Point(rect.x.toDouble(), rect.y.toDouble()), Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()), Scalar(255.0, 0.0, 0.0, 255.0), 2)
                    }
                }
                gray.release()
                hierarchy.release()
            }
            "orb_create", "orb", "sift_create", "sift", "drawkeypoints" -> {
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                val keypoints = MatOfKeyPoint()
                try {
                    if (task.lowercase().contains("sift")) {
                         if (cachedSIFT == null) cachedSIFT = SIFT.create()
                         cachedSIFT!!.detect(gray, keypoints)
                    } else {
                         if (cachedORB == null) cachedORB = ORB.create()
                         cachedORB!!.detect(gray, keypoints)
                    }
                } catch (e: Exception) {}
                src.copyTo(dst)
                Features2d.drawKeypoints(src, keypoints, dst, Scalar(0.0, 255.0, 0.0, 255.0), Features2d.DrawMatchesFlags_DRAW_RICH_KEYPOINTS)
                gray.release()
                keypoints.release()
            }
            "houghlines" -> {
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                val edges = Mat()
                Imgproc.Canny(gray, edges, 50.0, 150.0)
                val lines = Mat()
                Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI/180.0, 50, 50.0, 10.0)
                src.copyTo(dst)
                for (x in 0 until lines.rows()) {
                    val l = lines.get(x, 0)
                    if (l != null && l.size >= 4) {
                        Imgproc.line(dst, Point(l[0], l[1]), Point(l[2], l[3]), Scalar(0.0, 0.0, 255.0, 255.0), 3, Imgproc.LINE_AA, 0)
                    }
                }
                gray.release()
                edges.release()
                lines.release()
            }
            "houghcircles" -> {
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.medianBlur(gray, gray, 5)
                val circles = Mat()
                Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1.0, gray.rows()/16.0, 100.0, 30.0, 1, 30)
                src.copyTo(dst)
                if (circles.cols() > 0) {
                    for (x in 0 until circles.cols()) {
                        val c = circles.get(0, x)
                        if (c != null && c.size >= 3) {
                            val center = Point(Math.round(c[0]).toDouble(), Math.round(c[1]).toDouble())
                            val radius = Math.round(c[2]).toInt()
                            Imgproc.circle(dst, center, radius, Scalar(255.0, 0.0, 255.0, 255.0), 3, 8, 0)
                        }
                    }
                }
                gray.release()
                circles.release()
            }
            "equalizehist" -> {
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.equalizeHist(gray, dst)
                gray.release()
            }
            "qrcodedetector", "qrcode" -> {
                src.copyTo(dst)
                try {
                    val gray = Mat()
                    Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                    if (cachedQRDetector == null) cachedQRDetector = QRCodeDetector()
                    val points = Mat()
                    val result = cachedQRDetector!!.detectAndDecode(gray, points)
                    if (points.rows() > 0 && points.cols() >= 4) {
                        for (i in 0 until points.cols()) {
                            val p1 = points.get(0, i)
                            val p2 = points.get(0, (i+1)%4)
                            if (p1 != null && p2 != null && p1.size >= 2 && p2.size >= 2) {
                                val pt1 = Point(p1[0], p1[1])
                                val pt2 = Point(p2[0], p2[1])
                                Imgproc.line(dst, pt1, pt2, Scalar(0.0, 255.0, 0.0, 255.0), 4)
                            }
                        }
                    }
                    gray.release()
                    points.release()
                } catch (e: Exception) { }
            }
            "line", "rectangle", "circle", "puttext", "polylines", "fillpoly" -> {
                src.copyTo(dst)
                val w = src.cols().toDouble()
                val h = src.rows().toDouble()
                when (task.lowercase().replace("cv2.", "")) {
                    "line" -> Imgproc.line(dst, Point(w*0.1, h*0.1), Point(w*0.9, h*0.9), Scalar(0.0, 255.0, 0.0, 255.0), 5)
                    "circle" -> Imgproc.circle(dst, Point(w/2, h/2), (Math.min(w, h)/4).toInt(), Scalar(0.0, 0.0, 255.0, 255.0), 5)
                    "puttext" -> Imgproc.putText(dst, "OpenCV Text", Point(w*0.1, h*0.5), Imgproc.FONT_HERSHEY_SIMPLEX, w/500.0, Scalar(255.0, 255.0, 0.0, 255.0), 3)
                    else -> Imgproc.rectangle(dst, Point(w*0.2, h*0.2), Point(w*0.8, h*0.8), Scalar(255.0, 0.0, 0.0, 255.0), 5) // rectangle, polylines, fillPoly fallback
                }
            }
            "bitwise_not", "bitwise_and", "bitwise_or", "bitwise_xor", "add", "subtract" -> {
                // Performing op on itself for demonstration of binary ops
                when (task.lowercase().replace("cv2.", "")) {
                    "bitwise_not" -> Core.bitwise_not(src, dst)
                    "bitwise_and" -> Core.bitwise_and(src, src, dst)
                    "bitwise_or" -> Core.bitwise_or(src, src, dst)
                    "bitwise_xor" -> Core.bitwise_xor(src, src, dst)
                    "add" -> Core.add(src, src, dst)
                    "subtract" -> Core.subtract(src, src, dst)
                    else -> Core.bitwise_not(src, dst)
                }
            }
            "calchist", "inrange", "split", "merge" -> {
                val gray = Mat()
                Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                Core.inRange(gray, Scalar(100.0), Scalar(200.0), dst)
                gray.release()
            }
            "warpaffine", "warpperspective", "getperspectivetransform", "getrotationmatrix2d" -> {
                val center = Point(src.cols()/2.0, src.rows()/2.0)
                val rotMat = Imgproc.getRotationMatrix2D(center, 45.0, 1.0)
                Imgproc.warpAffine(src, dst, rotMat, src.size())
                rotMat.release()
            }
            "matchtemplate" -> {
                 val gray = Mat()
                 Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
                 // apply a flip on part of it as template
                 if (gray.rows() > 10 && gray.cols() > 10) {
                     val template = gray.submat(0, gray.rows()/2, 0, gray.cols()/2)
                     val result = Mat()
                     Imgproc.matchTemplate(gray, template, result, Imgproc.TM_CCOEFF_NORMED)
                     Core.normalize(result, dst, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8U)
                     template.release()
                     result.release()
                 } else {
                     src.copyTo(dst)
                 }
                 gray.release()
            }
            else -> {
                src.release()
                dst.release()
                return null
            }
        }
        
        val finalDst = if (dst.cols() > 0 && dst.rows() > 0) dst else src
        val resultBmp: Bitmap = if (finalDst.cols() == bitmap.width && finalDst.rows() == bitmap.height) {
            bitmap // Reuse input bitmap!
        } else {
            Bitmap.createBitmap(finalDst.cols(), finalDst.rows(), Bitmap.Config.ARGB_8888)
        }
        
        try {
            if (finalDst.channels() == 1) {
                val rgba = Mat()
                Imgproc.cvtColor(finalDst, rgba, Imgproc.COLOR_GRAY2RGBA)
                Utils.matToBitmap(rgba, resultBmp)
                rgba.release()
            } else if (finalDst.channels() == 3) {
                val rgba = Mat()
                Imgproc.cvtColor(finalDst, rgba, Imgproc.COLOR_RGB2RGBA)
                Utils.matToBitmap(rgba, resultBmp)
                rgba.release()
            } else {
                Utils.matToBitmap(finalDst, resultBmp)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        src.release()
        dst.release()
        return resultBmp
    } catch(e: Exception) {
        e.printStackTrace()
        return null
    }
}

