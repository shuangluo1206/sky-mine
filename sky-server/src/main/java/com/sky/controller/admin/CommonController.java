package com.sky.controller.admin;

import com.aliyun.oss.model.TagSet;
import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/admin/common")
@Api(tags="通用接口")
@Slf4j
public class CommonController {
    @Autowired
    private AliOssUtil aliOssUtil;
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String>upload(MultipartFile file){
        log.info("文件上传:{}",file);

            String originalFilename = file.getOriginalFilename();
            //截取源文件名都后缀    最后组合格式为：xxxxx.png
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
//            System.out.println("截取后的文件名字："+extension);
            //构造新文件的名字
            String objectName = UUID.randomUUID().toString() + extension;
        try {
            String filePath = aliOssUtil.upload(file.getBytes(), objectName);
            System.out.println("这次上传的文件最终filepath长什么样："+filePath);
            return Result.success(filePath);
        }catch(Exception exception) {
            log.error("文件上传失败：{}",exception.getMessage());
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}
