package com.epoint.testusb.host.utils;

import com.epoint.core.util.common.FileSavePath;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 作者： 戴亚伟
 * 创建时间： 2019/5/10 9:11
 * 版本： [1.0, 2019/5/10]
 * 版权： 江苏国泰新点软件有限公司
 * 描述： <描述>
 */
public class FileTransformation {

    /**
     * 文件转化为byte数组
     *
     * @param filePath
     * @return
     */
    public static byte[] fileToBytes(String filePath){
        byte[] buffer = null;
        try {
            File file = new File(filePath);
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1000);
            byte[] b = new byte[1000];
            int n;
            while ((n = fis.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            fis.close();
            bos.close();
            buffer = bos.toByteArray();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    /**
     * byte数组转化为文件
     *
     * @param bfile
     * @param fileName
     */
    public static void bytesToFile(byte[] bfile,String fileName){
        BufferedOutputStream bos = null;
        FileOutputStream fos = null;
        File file = null;
        String filePath = FileSavePath.getStoragePath();
        try {
            File dir = new File(filePath);
            if(!dir.exists()&&dir.isDirectory()){//判断文件目录是否存在
                dir.mkdirs();
            }
            file = new File(filePath+"\\"+fileName);
            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(bfile);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

}
