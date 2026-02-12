package com.alisa.mbg;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.JavaFormatter;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.DefaultJavaFormatter;
import org.mybatis.generator.api.dom.java.Field;
import org.mybatis.generator.api.dom.java.FullyQualifiedJavaType;
import org.mybatis.generator.api.dom.java.InnerClass;
import org.mybatis.generator.api.dom.java.Interface;
import org.mybatis.generator.api.dom.java.JavaVisibility;
import org.mybatis.generator.api.dom.java.Method;
import org.mybatis.generator.api.dom.java.TopLevelClass;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/*
 * 该插件可以配置生成entity、dto、mapper、service、controller等类
 * 且定义这些类的名字和生成的包名和位置
 */
public class VelocityCodeGeneratorPlugin extends PluginAdapter {
    private VelocityEngine velocityEngine;
    private String templatePath;
    private String dtoPackage;
    private String modelPackage;
    private String mapperPackage;
    private String enumPackage;
    private String targetControllerPackage;
    private String targetServicePackage;
    private String targetServiceImplPackage;
    private String targetProject;
    private java.util.List<String> modelNames = new java.util.ArrayList<>();

    @Override
    public void setProperties(Properties properties) {
        super.setProperties(properties);
        dtoPackage = properties.getProperty("dtoPackage", "com.example.demo.dto");
        super.setProperties(properties);
        templatePath = properties.getProperty("templatePath", "src/main/resources/templates");
        targetControllerPackage = properties.getProperty("targetControllerPackage",
                "com.example.demo.controller");
        targetServicePackage = properties.getProperty("targetServicePackage", "com.example.demo.service");
        targetServiceImplPackage = properties.getProperty("targetServiceImplPackage", "com.example.demo.serviceImpl");
        modelPackage = properties.getProperty("modelPackage", "com.example.demo.model");
        mapperPackage = properties.getProperty("mapperPackage", "com.example.demo.mapper");
        enumPackage = properties.getProperty("enumPackage", "com.example.demo.emums");
        targetProject = properties.getProperty("targetProject", "src/main/java");
        properties.setProperty("file.resource.loader.path", "target/classes");
        velocityEngine = new VelocityEngine(properties);
    }

    @Override
    public boolean validate(List<String> warnings) {
        return true;
    }

    @Override
    public void initialized(IntrospectedTable introspectedTable) {
        String oldExampleType = introspectedTable.getExampleType();
        String className = oldExampleType.substring(oldExampleType.lastIndexOf(".") + 1);
        String newClassName = className.replace("Example", "Filter");
        String newFullType = dtoPackage + "." + newClassName;

        introspectedTable.setExampleType(newFullType);
    }

    @Override
    public boolean clientGenerated(Interface interfaze, IntrospectedTable introspectedTable) {
        // 添加 @Mapper 注解
        FullyQualifiedJavaType mapperAnnotation = new FullyQualifiedJavaType("org.apache.ibatis.annotations.Mapper");
        interfaze.addImportedType(mapperAnnotation);
        interfaze.addAnnotation("@Mapper");
        return true;
    }

    @Override
    public boolean modelExampleClassGenerated(TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        generateCustomExample(topLevelClass, introspectedTable);
        return false;
    }

    @Override
    public boolean modelBaseRecordClassGenerated(TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        String modelName = introspectedTable.getFullyQualifiedTable().getDomainObjectName();
        modelNames.add(modelName);
        String pkType = "String";
        if (!introspectedTable.getPrimaryKeyColumns().isEmpty()) {
            pkType = introspectedTable.getPrimaryKeyColumns().get(0)
                    .getFullyQualifiedJavaType().getShortName();
        }

        generateControllerCode(modelName, targetControllerPackage, pkType);
        generateServiceCode(modelName, targetServicePackage, pkType);
        generateServiceImplCode(modelName, targetServiceImplPackage, pkType);
        generateTsModelCode(modelName, topLevelClass.getFields());
        generateUniversalEnum(introspectedTable);
        return true;
    }

    @Override
    public List<org.mybatis.generator.api.GeneratedJavaFile> contextGenerateAdditionalJavaFiles() {
        generateFlatApiConstants();
        return super.contextGenerateAdditionalJavaFiles();
    }

    private void generateCustomExample(TopLevelClass modelClass, IntrospectedTable introspectedTable) {
        String exampleClassName = modelClass.getType().getShortName();
        TopLevelClass exampleClass = new TopLevelClass(dtoPackage + "." + exampleClassName);
        exampleClass.setVisibility(JavaVisibility.PUBLIC);
        exampleClass.setFinal(true);
        exampleClass.addImportedType(new FullyQualifiedJavaType("java.util.List"));
        exampleClass.addImportedType(new FullyQualifiedJavaType("java.util.ArrayList"));

        for (Field iterable_element : modelClass.getFields()) {
            exampleClass.addField(iterable_element);
        }
        for (Method iterable_element : modelClass.getMethods()) {
            exampleClass.addMethod(iterable_element);
        }
        for (InnerClass innerClass : modelClass.getInnerClasses()) {
            exampleClass.addInnerClass(innerClass);
        }

        try {
            String filtPath = targetProject + "/" + dtoPackage.replace('.', '/') + "/" +
                    exampleClassName + ".java";
            File exampleFile = new File(filtPath);
            if (!exampleFile.getParentFile().exists()) {
                exampleFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(exampleFile)) {
                JavaFormatter formatter = new DefaultJavaFormatter();
                String formattedContent = formatter.getFormattedContent(exampleClass);
                writer.write(formattedContent);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateControllerCode(String modelName, String targetControllerPackage, String pkType) {
        VelocityContext velocityContext = new VelocityContext();

        velocityContext.put("modelName", modelName);
        velocityContext.put("modelNameLower", modelName.toLowerCase());
        velocityContext.put("modelPackageName", modelPackage);
        velocityContext.put("controllerPackageName", targetControllerPackage);
        velocityContext.put("servicePackageName", targetServicePackage);
        velocityContext.put("pkType", pkType);

        try {
            String filtPath = targetProject + "/" + targetControllerPackage.replace('.', '/') + "/" + modelName
                    + "Controller.java";
            File exampleFile = new File(filtPath);
            if (!exampleFile.getParentFile().exists()) {
                exampleFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(exampleFile)) {
                velocityEngine.mergeTemplate(templatePath + "/controller.vm", "UTF-8", velocityContext, writer);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateServiceCode(String modelName, String packageName, String pkType) {
        VelocityContext velocityContext = new VelocityContext();

        velocityContext.put("modelName", modelName);
        velocityContext.put("modelNameLower", modelName.toLowerCase());
        velocityContext.put("modelPackageName", modelPackage);
        velocityContext.put("servicePackageName", targetServicePackage);
        velocityContext.put("pkType", pkType);

        try {
            String filtPath = targetProject + "/" + targetServicePackage.replace('.', '/') + "/" + modelName
                    + "Service.java";
            File exampleFile = new File(filtPath);
            if (!exampleFile.getParentFile().exists()) {
                exampleFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(exampleFile)) {
                velocityEngine.mergeTemplate(templatePath + "/service.vm", "UTF-8", velocityContext, writer);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateServiceImplCode(String modelName, String packageName, String pkType) {
        VelocityContext velocityContext = new VelocityContext();

        velocityContext.put("modelName", modelName);
        velocityContext.put("modelNameLower", modelName.toLowerCase());
        velocityContext.put("modelPackageName", modelPackage);
        velocityContext.put("mapperPackageName", mapperPackage);
        velocityContext.put("servicePackageName", targetServicePackage);
        velocityContext.put("serviceImplPackageName", targetServiceImplPackage);
        velocityContext.put("pkType", pkType);

        try {
            String filtPath = targetProject + "/" + targetServiceImplPackage.replace('.', '/') + "/" + modelName
                    + "ServiceImpl.java";
            File exampleFile = new File(filtPath);
            if (!exampleFile.getParentFile().exists()) {
                exampleFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(exampleFile)) {
                velocityEngine.mergeTemplate(templatePath + "/serviceImpl.vm", "UTF-8", velocityContext, writer);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateTsModelCode(String modelName, List<Field> fields) {
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("modelName", modelName);
        velocityContext.put("fields", fields); // 核心：把 Java 字段列表传给模板
        try {
            // 设置 TS 文件输出路径，通常放在前端目录或 resources 下
            String tsPath = targetProject + "/ts/model/" + modelName
                    + ".ts";
            File tsFile = new File(tsPath);
            if (!tsFile.getParentFile().exists()) {
                tsFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(tsFile)) {
                // 使用新的 TS 模板
                velocityEngine.mergeTemplate(templatePath + "/model_ts.vm", "UTF-8", velocityContext, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateFlatApiConstants() {
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("modelNames", modelNames);

        try {
            String tsPath = targetProject + "/ts/api-constants.ts";
            File tsFile = new File(tsPath);
            if (!tsFile.getParentFile().exists()) {
                tsFile.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(tsFile)) {
                velocityEngine.mergeTemplate(templatePath + "/api_flat_ts.vm", "UTF-8", velocityContext, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateUniversalEnum(IntrospectedTable introspectedTable) {
        String domainName = introspectedTable.getFullyQualifiedTable().getDomainObjectName();
        String tableName = introspectedTable.getFullyQualifiedTable().getIntrospectedTableName();
        Properties props = introspectedTable.getTableConfiguration().getProperties();

        String constantProperty = props.getProperty("constantProperty");
        String keyProperty = props.getProperty("keyProperty");

        java.util.LinkedHashMap<String, String> columnMapping = new java.util.LinkedHashMap<>();
        for (Object key : props.keySet()) {
            String propName = (String) key;
            if (!Arrays.asList("generateEnum", "constantProperty", "keyProperty").contains(propName)) {
                columnMapping.put(propName, props.getProperty(propName));
            }
        }

        if (columnMapping.isEmpty())
            return;

        List<java.util.Map<String, String>> dataList = new java.util.ArrayList<>();
        String sql = "SELECT " + String.join(", ", columnMapping.values()) + " FROM " + tableName;

        try (java.sql.Connection conn = context.getConnection();
                java.sql.Statement stmt = conn.createStatement();
                java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                java.util.Map<String, String> row = new java.util.HashMap<>();
                for (java.util.Map.Entry<String, String> entry : columnMapping.entrySet()) {
                    row.put(entry.getKey(), rs.getString(entry.getValue()));
                }
                dataList.add(row);
            }
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
        }

        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("packageName", this.enumPackage);
        velocityContext.put("className", domainName);
        velocityContext.put("items", dataList);
        velocityContext.put("propNames", new java.util.ArrayList<>(columnMapping.keySet()));
        velocityContext.put("constantProperty", constantProperty);
        velocityContext.put("keyProperty", keyProperty);

        try {
            String packagePath = this.enumPackage.replace('.', '/');
            String enumPath = targetProject + "/" + packagePath + "/" + domainName + ".java";

            File file = new File(enumPath);
            if (!file.getParentFile().exists())
                file.getParentFile().mkdirs();

            try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
                velocityEngine.mergeTemplate(templatePath + "/universal_enum.vm", "UTF-8", velocityContext, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}