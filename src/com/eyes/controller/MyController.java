package com.eyes.controller;

import com.eyes.mvcframework.annotation.GPAutowared;
import com.eyes.mvcframework.annotation.GPController;
import com.eyes.mvcframework.annotation.GPRequestMapping;
import com.eyes.service.MyService;

@GPController
@GPRequestMapping("/test1")
public class MyController {
	
	@GPAutowared
	private MyService myService;
}
