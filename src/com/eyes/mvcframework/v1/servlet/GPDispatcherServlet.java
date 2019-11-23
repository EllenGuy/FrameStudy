package com.eyes.mvcframework.v1.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.eyes.mvcframework.annotation.GPAutowared;
import com.eyes.mvcframework.annotation.GPController;
import com.eyes.mvcframework.annotation.GPRequestMapping;
import com.eyes.mvcframework.annotation.GPRequestParam;
import com.eyes.mvcframework.annotation.GPService;


public class GPDispatcherServlet extends HttpServlet{
	//保存application.propeties配置文件的内容
	private Properties contextConfig = new Properties();
	//保存扫描到的所有类名
	private List<String> classNames = new ArrayList<String>();
	//传说中的ioc容器——底层是ConcurrentHashmap,这里用hashmap
	private Map<String, Object> ioc = new HashMap<String, Object>();
	//保存url和Method的对应关系
	//private Map<String, Method> handlerMapping = new HashMap<String, Method>();
	private List<Handler> handlerMapping = new ArrayList<Handler>();
	
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		// TODO Auto-generated method stub
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		// TODO Auto-generated method stub
		try {
			this.doDispatch(req, resp);
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		//1.加载配置文件
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		//2.扫描相关的类
		doScanner(contextConfig.getProperty("scanPackage"));
		//3.初始化扫描到的类，并将它们放入IOC容器
		doInstance();
		//4.完成依赖注入
		doAutowired();
		//5.初始化HandlerMapping
		initHandlerMapping();
	}

	private void doLoadConfig(String initParameter) {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(initParameter);
		try {
			contextConfig.load(is);
		} catch (Exception e) {
			e.printStackTrace();
		} finally{
			if (null != is) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			is = null;
		}
	}
	//扫描相关的类
	private void doScanner(String parentPackage) {
		//包名.类名的形式转换为包名/类名这种形式，实际就是把.替换为/
		URL url = this.getClass().getClassLoader().getResource("/" + parentPackage.replaceAll("\\.", "/"));
		File classPath = new File(url.getFile());
		for (File file : classPath.listFiles()) {
			if (file.isDirectory()) {
				doScanner(parentPackage+"."+file.getName());
			}else{
				if (!file.getName().endsWith(".class")) continue;
				String className = parentPackage+file.getName().replace(".class", "");
				classNames.add(className);
			}
		}

	}
	private void doInstance() {
		//初始化，为DI做准备
		if (classNames.isEmpty()) return;

		try {
			for (String className : classNames) {
				Class<?> clazz = Class.forName(className);
				if (clazz.isAnnotationPresent(GPController.class)) {
					Object obj = clazz.newInstance();
					String beanName = lowerFirstCase(clazz.getSimpleName());
					ioc.put(beanName, obj);
					
				}else if(clazz.isAnnotationPresent(GPService.class)){
					//获取自定义bean名称
					String beanName = clazz.getAnnotation(GPService.class).value();
					if ("".equals(beanName.trim())) {
						beanName = lowerFirstCase(clazz.getSimpleName());
					}
					Object obj = clazz.newInstance();
					ioc.put(beanName, obj);
				}else continue;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}


	}
	//自动注入
	private void doAutowired() {
		if (ioc.isEmpty()) return;
		
		for (Map.Entry<String, Object> entry: ioc.entrySet()) {
			//通过对象的反射，拿到class字节文件，进而拿到所有的成员变量
			Field [] fields = entry.getValue().getClass().getFields();
			for (Field field : fields) {
				//没有注解，不需要自动注入
				if (!field.isAnnotationPresent(GPAutowared.class)) continue;
				//有注解，需要注入
				String beanName = field.getAnnotation(GPAutowared.class).value().trim();
				//没有自定义注入变量名,按照类型来注入
				if (beanName == null || "".equals(beanName)) {
					beanName = lowerFirstCase(field.getType().getName());
				}
				//暴力访问private修饰的数据
				field.setAccessible(true);
				//通过反射给成员变量赋值
				try {
					field.set(entry.getValue()/*类名*/, ioc.get(beanName)/*变量对象*/);
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
	}
	//初始化
	private void initHandlerMapping() {
		if (ioc.isEmpty()) return;
		for(Map.Entry<String, Object> entry: ioc.entrySet()){
			//遍历ioc容器，对ioc容器中有Controller注解的进行requestMapping注解
			Class<?> clazz = entry.getValue().getClass();
			if (!clazz.isAnnotationPresent(GPController.class)) continue;
			//获取类上面的路径
			String baseUrl = "";
			if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
				baseUrl = clazz.getAnnotation(GPRequestMapping.class).value().trim();
			}
			//获取方法上面的路径
			Method [] methods = clazz.getMethods();
			for (Method method : methods) {
				String childUrl = "";
				if (method.isAnnotationPresent(GPRequestMapping.class)) {
					childUrl = method.getAnnotation(GPRequestMapping.class).value();
					//正则处理多个/的问题
					String url = (baseUrl + "/" + childUrl).replaceAll("/+", "/");
					//handlerMapping.put(url, method);
					handlerMapping.add(new Handler(Pattern.compile(url), entry.getValue(), method));
					System.out.println("mapped: "+url+":"+method);
				}
			}
		}
	}

	private void doDispatch(HttpServletRequest req, HttpServletResponse resp){

		Handler handler = getHandler(req);
		//if (!this.handlerMapping.containsKey(url)) {
		if (handler == null) {
			try {
				resp.getWriter().write("404 not found!");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		//获取方法的参数列表
		Class<?> [] paramTypes = handler.getParamTypes();
		Object[] paramValues = new Object[paramTypes.length];
		Map<String, String[]> params = req.getParameterMap();
		
		for (Object object : paramValues) {
			
		}
		//invoke方法，第一个参数：方法所在的实例；第二个参数：调用时所需要的实参
		//method.invoke(ioc.get(beanName), new Object[](req.getpa))
	}
	
	private String lowerFirstCase(String str){
		if (str == null || str.isEmpty()) return str;
		
		char[] chars = str.toCharArray();
		chars[0] += 32;
		return new String(chars);
	}
	
	private class Handler{
		protected Object controller; //保存方法对应的实例
		protected Method method;	 //映射的方法
		protected Pattern pattern;	 //正则
		protected Map<String, Integer> paramIndexMapping;	//参数顺序
		protected Handler(Pattern pattern, Object controller, Method method){
			this.controller = controller;
			this.pattern = pattern;
			this.method = method;
			paramIndexMapping = new HashMap<String, Integer>();
			putParamIndexMapping(method);
		}
		public Class<?>[] getParamTypes() {
			// TODO Auto-generated method stub
			return null;
		}
		private void putParamIndexMapping(Method method) {
			Annotation[][] pa = method.getParameterAnnotations();//返回该方法各个参数的各个注解，所以是个二维数组
			for (int i = 0; i < pa.length; i++) {
				for (Annotation anno : pa[i]) {
					if (anno instanceof GPRequestParam) {
						String paramName = ((GPRequestParam)anno).value();
						if (!"".equals(paramName.trim())) {
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}
			//提取方法中的request和response参数
			Class<?>[] paramTypes = method.getParameterTypes();
			for (int i = 0; i < pa.length; i++) {
				Class<?> type = paramTypes[i];
				if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
					paramIndexMapping.put(type.getName(), i);
				}
			}
		}
	}
	
	private Handler getHandler(HttpServletRequest req){
		if (handlerMapping.isEmpty()) return null;
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		url = url.replaceAll(contextPath, "").replaceAll("/+", "/");
		for (Handler handle : handlerMapping) {
			Matcher matcher = handle.pattern.matcher(url);
			if (!matcher.matches()) continue;
			return handle;
		}
		return null;
	}
	
	private Object convert(Class<?> type, String value){
		if (Integer.class == type) {
			return Integer.valueOf(value);
		}
		return value;
	}
}


