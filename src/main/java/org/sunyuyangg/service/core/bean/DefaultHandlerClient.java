package org.sunyuyangg.service.core.bean;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ibm.staf.STAFHandle;
import com.ibm.staf.STAFResult;
import com.ibm.staf.STAFUtil;
import org.pmw.tinylog.Logger;
import org.springframework.util.StringUtils;
import org.sunyuyangg.service.core.Util;

import java.util.List;
import java.util.Map;

public class DefaultHandlerClient implements HandlerClient {

    private final String DEFAULT_QUEUE_TIMEOUT = "5m";

    private STAFHandle handle;
    private String localMachineName;
    private String timeOut = "";

    public DefaultHandlerClient(String serviceName) throws Exception {
        handle = new STAFHandle("STAF/Service/" + serviceName);

        // Resolve the machine name variable for the local machine
        STAFResult res = STAFUtil.resolveInitVar("{STAF/Config/Machine}", handle);
        if (res.rc != STAFResult.Ok) {
            throw new Exception("can not get machine");
        }
        localMachineName = res.result;
    }

    public DefaultHandlerClient(STAFHandle handle, String localMachineName) {
        this.handle = handle;
        this.localMachineName = localMachineName;
    }

    public void setTimeOut(String timeOut) {
        this.timeOut = timeOut;
    }

    @Override
    public STAFResult waitForQueueType(String... types) {
        if (StringUtils.isEmpty(timeOut)) {
            return waitForQueueTypeWithTimeout(DEFAULT_QUEUE_TIMEOUT, types);
        }
        return waitForQueueTypeWithTimeout(timeOut, types);
    }

    @Override
    public STAFResult submit(Object object, String type) {
        return this.handle.submit2(this.localMachineName, "QUEUE", createRequest(object, type));
    }

    private STAFResult waitForQueueTypeWithTimeout(String timeout, String... types) {
        StringBuffer request = new StringBuffer();

        request.append(" GET WAIT ")
                .append(timeout);

        for (String type : types) {
            if (StringUtils.isEmpty(type)) {
                continue;
            }
            request.append(" TYPE ").append(type);
        }

        return handle.submit2(localMachineName, "QUEUE", request.toString());
    }

    private String createRequest(Object object, String type) {
        StringBuffer request = new StringBuffer();
        request.append(" QUEUE ");
        request.append(" TYPE ").append(type);
        String result;
        try {
            if (object instanceof String) {
                result = (String) object;
            } else {
                result = Util.objectMapper().writeValueAsString(object);
            }

        } catch (JsonProcessingException e) {
            Logger.error(e);
            result = e.getMessage();
        }
        request.append(" MESSAGE ").append(STAFUtil.wrapData(result));
        return request.toString();
    }
}
