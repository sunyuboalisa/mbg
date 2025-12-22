package com.alisa.mbg;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.mybatis.generator.api.MyBatisGenerator;
import org.mybatis.generator.config.*;
import org.mybatis.generator.config.xml.ConfigurationParser;
import org.mybatis.generator.exception.InvalidConfigurationException;
import org.mybatis.generator.internal.DefaultShellCallback;

public class MbgApplication {

	public static void main(String[] args) {
		try {
			File buildDir = new File("output");
			if (buildDir.exists()) {
				deleteFolder(buildDir);
			}
			buildDir.mkdir();

			List<String> warnings = new ArrayList<String>();
			boolean overwrite = true;
			File configFile = new File("generatorConfig.xml");
			ConfigurationParser cp = new ConfigurationParser(warnings);
			Configuration config = cp.parseConfiguration(configFile);
			DefaultShellCallback callback = new DefaultShellCallback(overwrite);
			MyBatisGenerator myBatisGenerator = new MyBatisGenerator(config, callback, warnings);
			myBatisGenerator.generate(null);

			for (String warning : warnings) {
				System.out.println(warning);
			}

			System.out.println("生成完成。");
		} catch (InvalidConfigurationException e) {
			System.out.println("Invalid configuration: " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// 递归删除文件夹及其内容
	public static boolean deleteFolder(File folder) {
		if (folder.isDirectory()) {
			// 获取文件夹中的所有文件和子文件夹
			File[] files = folder.listFiles();
			if (files != null) {
				for (File file : files) {
					// 递归删除每一个文件或子文件夹
					if (!deleteFolder(file)) {
						return false; // 如果某个文件或子文件夹删除失败，则返回 false
					}
				}
			}
		}

		// 删除空文件夹或文件
		return folder.delete();
	}
}
