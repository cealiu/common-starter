package com.duowan.util;

public class DataSecurityService {


	public static String aesEncrypt(String data){

		return "test+"+data;
	}

	public static String aesDecrypt(String data){
		return data.substring(5);
	}
}
