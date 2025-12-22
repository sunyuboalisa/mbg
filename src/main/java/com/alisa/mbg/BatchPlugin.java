package com.alisa.mbg;

import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.Document;
import org.mybatis.generator.api.dom.xml.TextElement;
import org.mybatis.generator.api.dom.xml.XmlElement;

import java.util.List;
import java.util.stream.Collectors;

public class BatchPlugin extends PluginAdapter {

    @Override
    public boolean validate(List<String> warnings) {
        return true;
    }

    /**
     * Java 接口增强
     */
    @Override
    public boolean clientGenerated(Interface interfaze, IntrospectedTable introspectedTable) {
        String recordType = introspectedTable.getBaseRecordType();

        FullyQualifiedJavaType pkType;
        if (!introspectedTable.getPrimaryKeyColumns().isEmpty()) {
            pkType = introspectedTable.getPrimaryKeyColumns().get(0).getFullyQualifiedJavaType();
        } else {
            pkType = new FullyQualifiedJavaType("java.lang.String");
        }

        FullyQualifiedJavaType listEntityType = FullyQualifiedJavaType.getNewListInstance();
        listEntityType.addTypeArgument(new FullyQualifiedJavaType(recordType));

        FullyQualifiedJavaType listIdType = FullyQualifiedJavaType.getNewListInstance();
        listIdType.addTypeArgument(pkType);

        interfaze.addImportedType(listEntityType);
        interfaze.addImportedType(pkType);

        interfaze.addMethod(generateMethod("upsert", new Parameter(new FullyQualifiedJavaType(recordType), "record")));
        interfaze.addMethod(generateMethod("batchUpsert", new Parameter(listEntityType, "list", "@Param(\"list\")")));
        interfaze.addMethod(generateMethod("batchDelete", new Parameter(listIdType, "list", "@Param(\"list\")")));

        return true;
    }

    @Override
    public boolean sqlMapDocumentGenerated(Document document, IntrospectedTable introspectedTable) {
        String tableName = introspectedTable.getFullyQualifiedTableNameAtRuntime();
        List<IntrospectedColumn> allColumns = introspectedTable.getAllColumns();
        List<IntrospectedColumn> pkColumns = introspectedTable.getPrimaryKeyColumns();

        document.getRootElement().addElement(buildBatchUpsertXml(tableName, allColumns, introspectedTable));

        if (pkColumns != null && !pkColumns.isEmpty()) {
            document.getRootElement().addElement(buildUpsertXml(tableName, allColumns, introspectedTable));
            document.getRootElement().addElement(buildBatchDeleteXml(tableName, pkColumns.get(0)));
        } else {
            System.err.println(">>> [BatchPlugin] 跳过表 " + tableName + " 的主键方法，未识别到主键。");
        }
        return true;
    }

    // ---------------- XML 构造方法 ----------------

    private XmlElement buildBatchDeleteXml(String tableName, IntrospectedColumn pk) {
        XmlElement xml = new XmlElement("delete");
        xml.addAttribute(new Attribute("id", "batchDelete"));
        xml.addAttribute(new Attribute("parameterType", "java.util.List"));

        xml.addElement(new TextElement("delete from " + tableName + " where " + pk.getActualColumnName() + " in "));

        XmlElement foreach = new XmlElement("foreach");
        foreach.addAttribute(new Attribute("collection", "list"));
        foreach.addAttribute(new Attribute("item", "id")); // 既然传的是 ID 列表，item 就叫 id
        foreach.addAttribute(new Attribute("open", "("));
        foreach.addAttribute(new Attribute("separator", ","));
        foreach.addAttribute(new Attribute("close", ")"));

        // 【关键】因为参数是 List<String>，直接使用 #{id} 即可
        foreach.addElement(new TextElement("#{id}"));

        xml.addElement(foreach);
        return xml;
    }

    private String buildUpsertUpdateSql(List<IntrospectedColumn> columns, IntrospectedTable table) {
        String updates = columns.stream()
                .filter(c -> !isPrimaryKey(c, table))
                .map(c -> c.getActualColumnName() + " = values(" + c.getActualColumnName() + ")")
                .collect(Collectors.joining(", "));
        return "on duplicate key update " + updates;
    }

    // ---------------- 辅助方法 ----------------

    private Method generateMethod(String name, Parameter parameter) {
        Method method = new Method(name);
        method.setVisibility(JavaVisibility.PUBLIC);
        method.setReturnType(FullyQualifiedJavaType.getIntInstance());
        method.addParameter(parameter);

        // 【解决大括号的关键】设置为抽象方法，生成器会自动以分号结尾
        method.setAbstract(true);

        return method;
    }

    private boolean isPrimaryKey(IntrospectedColumn column, IntrospectedTable table) {
        return table.getPrimaryKeyColumns().stream()
                .anyMatch(pk -> pk.getActualColumnName().equalsIgnoreCase(column.getActualColumnName()));
    }

    private XmlElement buildBatchUpsertXml(String tableName, List<IntrospectedColumn> columns,
            IntrospectedTable table) {
        XmlElement xml = new XmlElement("insert");
        xml.addAttribute(new Attribute("id", "batchUpsert"));
        xml.addAttribute(new Attribute("parameterType", "java.util.List"));
        xml.addElement(new TextElement("insert into " + tableName + " (" +
                columns.stream().map(IntrospectedColumn::getActualColumnName).collect(Collectors.joining(", "))
                + ") values "));
        XmlElement foreach = new XmlElement("foreach");
        foreach.addAttribute(new Attribute("collection", "list"));
        foreach.addAttribute(new Attribute("item", "item"));
        foreach.addAttribute(new Attribute("separator", ","));
        foreach.addElement(new TextElement(columns.stream()
                .map(c -> "#{item." + c.getJavaProperty() + ",jdbcType=" + c.getJdbcTypeName() + "}")
                .collect(Collectors.joining(", ", "(", ")"))));
        xml.addElement(foreach);
        xml.addElement(new TextElement(buildUpsertUpdateSql(columns, table)));
        return xml;
    }

    private XmlElement buildUpsertXml(String tableName, List<IntrospectedColumn> columns, IntrospectedTable table) {
        XmlElement xml = new XmlElement("insert");
        xml.addAttribute(new Attribute("id", "upsert"));
        xml.addAttribute(new Attribute("parameterType", table.getBaseRecordType()));
        xml.addElement(new TextElement("insert into " + tableName + " (" +
                columns.stream().map(IntrospectedColumn::getActualColumnName).collect(Collectors.joining(", "))
                + ") values "));
        xml.addElement(new TextElement(columns.stream()
                .map(c -> "#{" + c.getJavaProperty() + ",jdbcType=" + c.getJdbcTypeName() + "}")
                .collect(Collectors.joining(", ", "(", ")"))));
        xml.addElement(new TextElement(buildUpsertUpdateSql(columns, table)));
        return xml;
    }
}