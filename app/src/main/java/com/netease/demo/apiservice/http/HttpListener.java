package com.netease.demo.apiservice.http;

import com.netease.libs.apiservice.anno.ApiServiceClassAnno;

/**
 * Created by zyl06 on 2018/11/5.
 */
@ApiServiceClassAnno(allPublicNormalApi = true, includeSuperApi = true)
public interface HttpListener extends BaseHttpListener {

    void onCancel();
}