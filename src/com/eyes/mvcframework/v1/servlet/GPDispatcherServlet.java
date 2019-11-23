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
	//����application.propeties�����ļ�������
	private Properties contextConfig = new Properties();
	//����ɨ�赽����������
	private List<String> classNames = new ArrayList<String>();
	//��˵�е�ioc���������ײ���ConcurrentHashmap,������hashmap
	private Map<String, Object> ioc = new HashMap<String, Object>();
	//����url��Method�Ķ�Ӧ��ϵ
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
		//1.���������ļ�
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		//2.ɨ����ص���
		doScanner(contextConfig.getProperty("scanPackage"));
		//3.��ʼ��ɨ�赽���࣬�������Ƿ���IOC����
		doInstance();
		//4.�������ע��
		doAutowired();
		//5.��ʼ��HandlerMapping
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
	//ɨ����ص���
	private void doScanner(String parentPackage) {
		//����.��������ʽת��Ϊ����/����������ʽ��ʵ�ʾ��ǰ�.�滻Ϊ/
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
		//��ʼ����ΪDI��׼��
		if (classNames.isEmpty()) return;

		try {
			for (String className : classNames) {
				Class<?> clazz = Class.forName(className);
				if (clazz.isAnnotationPresent(GPController.class)) {
					Object obj = clazz.newInstance();
					String beanName = lowerFirstCase(clazz.getSimpleName());
					ioc.put(beanName, obj);
					
				}else if(clazz.isAnnotationPresent(GPService.class)){
					//��ȡ�Զ���bean����
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
	//�Զ�ע��
	private void doAutowired() {
		if (ioc.isEmpty()) return;
		
		for (Map.Entry<String, Object> entry: ioc.entrySet()) {
			//ͨ������ķ��䣬�õ�class�ֽ��ļ��������õ����еĳ�Ա����
			Field [] fields = entry.getValue().getClass().getFields();
			for (Field field : fields) {
				//û��ע�⣬����Ҫ�Զ�ע��
				if (!field.isAnnotationPresent(GPAutowared.class)) continue;
				//��ע�⣬��Ҫע��
				String beanName = field.getAnnotation(GPAutowared.class).value().trim();
				//û���Զ���ע�������,����������ע��
				if (beanName == null || "".equals(beanName)) {
					beanName = lowerFirstCase(field.getType().getName());
				}
				//��������private���ε�����
				field.setAccessible(true);
				//ͨ���������Ա������ֵ
				try {
					field.set(entry.getValue()/*����*/, ioc.get(beanName)/*��������*/);
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
	}
	//��ʼ��
	private void initHandlerMapping() {
		if (ioc.isEmpty()) return;
		for(Map.Entry<String, Object> entry: ioc.entrySet()){
			//����ioc��������ioc��������Controllerע��Ľ���requestMappingע��
			Class<?> clazz = entry.getValue().getClass();
			if (!clazz.isAnnotationPresent(GPController.class)) continue;
			//��ȡ�������·��
			String baseUrl = "";
			if (clazz.isAnnotationPresent(GPRequestMapping.class)) {
				baseUrl = clazz.getAnnotation(GPRequestMapping.class).value().trim();
			}
			//��ȡ���������·��
			Method [] methods = clazz.getMethods();
			for (Method method : methods) {
				String childUrl = "";
				if (method.isAnnotationPresent(GPRequestMapping.class)) {
					childUrl = method.getAnnotation(GPRequestMapping.class).value();
					//��������/������
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
		//��ȡ�����Ĳ����б�
		Class<?> [] paramTypes = handler.getParamTypes();
		Object[] paramValues = new Object[paramTypes.length];
		Map<String, String[]> params = req.getParameterMap();
		
		for (Object object : paramValues) {
			
		}
		//invoke��������һ���������������ڵ�ʵ�����ڶ�������������ʱ����Ҫ��ʵ��
		//method.invoke(ioc.get(beanName), new Object[](req.getpa))
	}
	
	private String lowerFirstCase(String str){
		if (str == null || str.isEmpty()) return str;
		
		char[] chars = str.toCharArray();
		chars[0] += 32;
		return new String(chars);
	}
	
	private class Handler{
		protected Object controller; //���淽����Ӧ��ʵ��
		protected Method method;	 //ӳ��ķ���
		protected Pattern pattern;	 //����
		protected Map<String, Integer> paramIndexMapping;	//����˳��
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
			Annotation[][] pa = method.getParameterAnnotations();//���ظ÷������������ĸ���ע�⣬�����Ǹ���ά����
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
			//��ȡ�����е�request��response����
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


