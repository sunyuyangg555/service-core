package org.sunyuyangg.service.core;

import com.ibm.staf.*;
import com.ibm.staf.service.STAFServiceInterfaceLevel30;
import org.pmw.tinylog.Logger;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SourceFilteringListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.sunyuyangg.service.core.method.HelpMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class FrameworkService implements STAFServiceInterfaceLevel30, EnvironmentCapable {

    private static final String APPLICATION_CONTEXT_ID_PREFIX = "FRAMEWORK_CONTEXT";
    protected String serviceName;
    protected STAFHandle handle;
    public static String lineSep = "";
    protected String localMachineName = "";

    int serviceInvalidSerialNumber;

    private static String helpMsg ;
    private static List<HelpMessage> helpMessages = new ArrayList<>();
    private static STAFMapClassDefinition helpDefinition;

    @Nullable
    private ConfigurableEnvironment environment;
    /** Explicit context config location */
    @Nullable
    private String contextConfigLocation;
    private Class<?> annotatedClass;

    private ApplicationContext context;
    public static final Class<?> DEFAULT_CONTEXT_CLASS = AnnotationConfigApplicationContext.class;
    private Class<?> contextClass = DEFAULT_CONTEXT_CLASS;

    /** Flag used to detect whether onRefresh has already been called */
    private boolean refreshEventReceived = false;
    /** ApplicationContext id to assign */
    @Nullable
    private String contextId;
    private final String version;

    public FrameworkService(int serviceInvalidSerialNumber, String version) {
        this.serviceInvalidSerialNumber = serviceInvalidSerialNumber;
        this.version = version;
    }

    public FrameworkService(int serviceInvalidSerialNumber, String version, ApplicationContext applicationContext) {
        this.serviceInvalidSerialNumber = serviceInvalidSerialNumber;
        this.version = version;
        this.context = applicationContext;
    }

    private void initServiceBean() {
        Logger.info("Initializing  FrameworkService '" + getServiceName() + "'");
        long startTime = System.currentTimeMillis();
        long elapsedTime = System.currentTimeMillis() - startTime;
        try {
            this.context = initApplicationContext();
            initFrameworkService();
        }catch (Exception e) {
            Logger.error("Context initialization failed : {}", e);
            throw e;
        }

        Logger.info("FrameworkService '" + getServiceName() + "': initialization completed in " + elapsedTime + " ms");
    }

    protected void registerBean(String beanName, Object bean) {
        ConfigurableApplicationContext configContext = (ConfigurableApplicationContext)this.context;
        SingletonBeanRegistry beanRegistry = configContext.getBeanFactory();
        beanRegistry.registerSingleton(beanName, bean);
    }

    public void setContextConfigLocation(@Nullable String contextConfigLocation) {
        this.contextConfigLocation = contextConfigLocation;
    }

    @Nullable
    public String getContextConfigLocation() {
        return contextConfigLocation;
    }

    protected  void initFrameworkService(){

    }

    protected  ApplicationContext initApplicationContext(){
        ApplicationContext applicationContext = null;
        if(this.context != null) {
            // A context instance was injected at construction time -> use it
            applicationContext = this.context;
            if(applicationContext instanceof ConfigurableApplicationContext) {
                ConfigurableApplicationContext configurableApplicationContext = (ConfigurableApplicationContext) applicationContext;
                if(!configurableApplicationContext.isActive()) {
                    configureAndRefreshApplicationContext(configurableApplicationContext);
                }
            }
        }

        if(applicationContext == null) {
            // No context instance is defined for this service -> create a local one
            applicationContext = creatApplicationContext();
        }

        if (!this.refreshEventReceived) {
            // Either the context is not a ConfigurableApplicationContext with refresh
            // support or the context injected at construction time had already been
            // refreshed -> trigger initial onRefresh manually here.
            onRefresh(applicationContext);
        }

        return applicationContext;
    }

    /**
     * Template method which can be overridden to add service-specific refresh work.
     * Called after successful context refresh.
     * <p>This implementation is empty.
     * @param context the current ApplicationContext
     */
    protected void onRefresh(ApplicationContext context) {
        // For subclasses: do nothing by default.
    }

    /**
     * ApplicationListener endpoint that receives events from this servlet's WebApplicationContext
     * only, delegating to {@code onApplicationEvent} on the FrameworkServlet instance.
     */
    private class ContextRefreshListener implements ApplicationListener<ContextRefreshedEvent> {

        @Override
        public void onApplicationEvent(ContextRefreshedEvent event) {
            FrameworkService.this.onApplicationEvent(event);
        }
    }

    /**
     * Callback that receives refresh events from this service's ApplicationContext.
     * <p>The default implementation calls {@link #onRefresh},
     * triggering a refresh of this servlet's context-dependent state.
     * @param event the incoming ApplicationContext event
     */
    public void onApplicationEvent(ContextRefreshedEvent event) {
        this.refreshEventReceived = true;
        onRefresh(event.getApplicationContext());
    }

    /**
     * Return the custom context class.
     */
    public Class<?> getContextClass() {
        return this.contextClass;
    }

    protected ConfigurableEnvironment createEnvironment() {
        return new StandardEnvironment();
    }

    @Override
    public ConfigurableEnvironment getEnvironment() {
        if (this.environment == null) {
            this.environment = createEnvironment();
        }
        return this.environment;
    }

    private ApplicationContext creatApplicationContext() {
        Class<?> contextClass = getContextClass();
        Logger.info("Service {} will create custom ApplicationContext context of class {}" , getServiceName(), contextClass.getName());
        if (!ConfigurableApplicationContext.class.isAssignableFrom(contextClass)) {
            throw new ApplicationContextException(
                    "Fatal initialization error in service with name '" + getServiceName() +
                            "': custom ApplicationContext class [" + contextClass.getName() +
                            "] is not of type ConfigurableApplicationContext");
        }
        AnnotationConfigApplicationContext annotationConfigApplicationContext = (AnnotationConfigApplicationContext) BeanUtils.instantiateClass(contextClass);
        annotationConfigApplicationContext.register(getAnnotatedClass());
        annotationConfigApplicationContext.setEnvironment(getEnvironment());
        configureAndRefreshApplicationContext(annotationConfigApplicationContext);

        return annotationConfigApplicationContext;
    }

    public Class<?> getAnnotatedClass() {
        return annotatedClass;
    }

    public void setAnnotatedClass(Class<?> annotatedClass) {
        this.annotatedClass = annotatedClass;
    }

    /**
     * Specify a custom ApplicationContext id,
     * to be used as serialization id for the underlying BeanFactory.
     */
    public void setContextId(@Nullable String contextId) {
        this.contextId = contextId;
    }

    /**
     * Return the custom ApplicationContext id, if any.
     */
    @Nullable
    public String getContextId() {
        return this.contextId;
    }

    protected void postProcessApplicationContext(ConfigurableApplicationContext configurableApplicationContext) {
    }

    protected  void configureAndRefreshApplicationContext(ConfigurableApplicationContext configurableApplicationContext){
        if (ObjectUtils.identityToString(configurableApplicationContext).equals(configurableApplicationContext.getId())) {
            // The application context id is still set to its original default value
            // -> assign a more useful id based on available information
            if (this.contextId != null) {
                configurableApplicationContext.setId(this.contextId);
            }
            else {
                // Generate default id...
                configurableApplicationContext.setId(APPLICATION_CONTEXT_ID_PREFIX +
                        ObjectUtils.getDisplayString(getLocalMachineName()) + '/' + getServiceName());
            }
        }

        configurableApplicationContext.addApplicationListener(new SourceFilteringListener(configurableApplicationContext, new ContextRefreshListener()));

        postProcessApplicationContext(configurableApplicationContext);
        configurableApplicationContext.refresh();
    }

    public void setContextClass(Class<?> contextClass) {
        this.contextClass = contextClass;
    }

    protected void initStrategies(ApplicationContext context) {

    }

    @Override
    public STAFResult init(InitInfo info) {
        try {
            serviceName = info.name;
            handle = new STAFHandle("STAF/Service/" + info.name);
        } catch (STAFException e) {
            Logger.error(e);
            return new STAFResult(STAFResult.STAFRegistrationError, e.toString());
        }

        try {

            // Resolve the line separator variable for the local machine
            STAFResult res = STAFUtil.resolveInitVar("{STAF/Config/Sep/Line}", handle);
            if (res.rc != STAFResult.Ok)
                return res;
            lineSep = res.result;

            // Resolve the machine name variable for the local machine
            res = STAFUtil.resolveInitVar("{STAF/Config/Machine}", handle);
            if (res.rc != STAFResult.Ok)
                return res;
            localMachineName = res.result;

            // Register Help Data
            registerHelpData(
                    serviceInvalidSerialNumber,
                    "Invalid serial number",
                    "A non-numeric value was specified for serial number");

            initHelpData();
            initServiceBean();
            initStrategies(context);

        } catch (Exception e) {
            return new STAFResult(STAFResult.UnknownError, e.toString());
        }
        return new STAFResult(STAFResult.Ok);
    }

    private void initHelpData() {
        // Assign the help text string for the service
        helpDefinition = new STAFMapClassDefinition(this.serviceName + "/help");
        helpDefinition.addKey("command", "Command");
        helpDefinition.addKey("options", "Options");
        helpDefinition.addKey("description", "Description");
    }

    public static void addHelpMessage(HelpMessage help) {
        Optional<HelpMessage> helpMessageOptional = helpMessages.stream().filter(helpMessage -> helpMessage.getCommand().equals(help.getCommand())).findAny();
        if(helpMessageOptional.isPresent()) {
            help.setCommand("");
        }
        helpMessages.add(help);
        helpMsg = null;
    }

    @Override
    public STAFResult acceptRequest(RequestInfo requestInfo) {
        STAFResult result = new STAFResult(STAFResult.Ok);
        try {
            String action = Util.getActionStr(requestInfo.request);
            if(action.equalsIgnoreCase("help")) {
                return getHelpResult();
            }

            if(action.equalsIgnoreCase("version")) {
                return new STAFResult(STAFResult.Ok, version);
            }
            doService(requestInfo, result);
            return result;
        } catch (Exception e) {
            Logger.error(e);
            return new STAFResult(STAFResult.UnknownError, e.getMessage());
        }
    }

    private STAFResult getHelpResult() {
        if(StringUtils.isEmpty(helpMsg)) {
            STAFMarshallingContext mc = new STAFMarshallingContext();
            mc.setMapClassDefinition(helpDefinition);
            List resultList = new ArrayList();
            if (helpMessages.size() > 0)
            {
                for(HelpMessage helpMessage: helpMessages) {
                    Map resultMap = helpDefinition.createInstance();
                    resultMap.put("command", helpMessage.getCommand());
                    resultMap.put("options", helpMessage.getOptions());
                    resultMap.put("description", helpMessage.getDescription());
                    resultList.add(resultMap);
                }
            }
            mc.setRootObject(resultList);
            helpMsg = mc.marshall();
        }

        return new STAFResult(STAFResult.Ok, helpMsg);
    }

    protected abstract void doService(RequestInfo request, STAFResult response) throws Exception;

    protected void setResponse(STAFResult response, int rc, String result) {
        response.rc = rc;
        response.result = result;
    }

    @Override
    public STAFResult term() {
        try {
            // Un-register Help Data
            unregisterHelpData(serviceInvalidSerialNumber);
            // Un-register the service handle
            handle.unRegister();

        } catch (STAFException ex) {
            return new STAFResult(STAFResult.STAFRegistrationError,
                    ex.toString());
        }
        return new STAFResult(STAFResult.Ok);
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getLocalMachineName() {
        return localMachineName;
    }


    public STAFHandle getSTAFHandle() {
        return handle;
    }

    //region > register help data
    // Register error codes for this service with the HELP service
    private void registerHelpData(int errorNumber, String info, String description) {
        STAFResult res = handle.submit2(
                "local", "HELP", "REGISTER SERVICE " + serviceName +
                        " ERROR " + errorNumber +
                        " INFO " + STAFUtil.wrapData(info) +
                        " DESCRIPTION " + STAFUtil.wrapData(description));
    }
    //endregion

    //region > unregister help data
    // Un-register error codes for this service with the HELP service
    private void unregisterHelpData(int errorNumber) {
        STAFResult res = handle.submit2(
                "local", "HELP", "UNREGISTER SERVICE " + serviceName +
                        " ERROR " + errorNumber);
    }
    //endregion

}
