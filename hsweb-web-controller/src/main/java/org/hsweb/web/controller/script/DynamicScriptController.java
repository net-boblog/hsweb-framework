package org.hsweb.web.controller.script;

import org.hsweb.web.core.logger.annotation.AccessLogger;
import org.hsweb.web.core.authorize.annotation.Authorize;
import org.hsweb.web.bean.po.role.Role;
import org.hsweb.web.bean.po.script.DynamicScript;
import org.hsweb.web.controller.GenericController;
import org.hsweb.web.core.message.ResponseMessage;
import org.hsweb.web.service.script.DynamicScriptService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 动态脚本控制器，继承自GenericController,使用rest+json
 * Created by generator 2015-10-27 14:42:34
 */
@RestController
@RequestMapping(value = "/script")
@AccessLogger("动态脚本")
@Authorize(module = "script")
public class DynamicScriptController extends GenericController<DynamicScript, String> {

    //默认服务类
    @Resource
    private DynamicScriptService dynamicScriptService;

    @Override
    public DynamicScriptService getService() {
        return this.dynamicScriptService;
    }


    @RequestMapping(value = "/compile", method = {RequestMethod.GET})
    @Authorize(action = "compile")
    public ResponseMessage compileAll() throws Exception {
        dynamicScriptService.compileAll();
        return ResponseMessage.ok("success");
    }

    @RequestMapping(value = "/compile/{id:.+}", method = {RequestMethod.GET})
    @Authorize(action = "compile")
    public ResponseMessage compile(@PathVariable("id") String id) throws Exception {
        dynamicScriptService.compile(id);
        return ResponseMessage.ok("success");
    }
}
