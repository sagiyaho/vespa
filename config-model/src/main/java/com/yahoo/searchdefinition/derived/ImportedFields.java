// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.ImportedComplexField;
import com.yahoo.searchdefinition.document.ImportedField;
import com.yahoo.vespa.config.search.ImportedFieldsConfig;

import java.util.Optional;

import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isArrayOfSimpleStruct;
import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isMapOfPrimitiveType;
import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isMapOfSimpleStruct;

/**
 * This class derives imported fields from search definition and produces imported-fields.cfg as needed by the search backend.
 *
 * @author geirst
 */
public class ImportedFields extends Derived implements ImportedFieldsConfig.Producer {

    private Optional<com.yahoo.searchdefinition.document.ImportedFields> importedFields = Optional.empty();

    public ImportedFields(Search search) {
        derive(search);
    }

    @Override
    protected void derive(Search search) {
        importedFields = search.importedFields();
    }

    @Override
    protected String getDerivedName() {
        return "imported-fields";
    }

    @Override
    public void getConfig(ImportedFieldsConfig.Builder builder) {
        if (importedFields.isPresent()) {
            importedFields.get().fields().forEach( (name, field) -> considerField(builder, field));
        }
    }

    private static boolean isNestedFieldName(String fieldName) {
        return fieldName.indexOf('.') != -1;
    }

    private static void considerField(ImportedFieldsConfig.Builder builder, ImportedField field) {
        if (field instanceof ImportedComplexField) {
            considerComplexField(builder, (ImportedComplexField) field);
        } else {
            considerSimpleField(builder, field);
        }
    }

    private static void considerComplexField(ImportedFieldsConfig.Builder builder, ImportedComplexField field) {
        ImmutableSDField targetField = field.targetField();
        if (targetField.getDataType().equals(PositionDataType.INSTANCE) ||
                targetField.getDataType().equals(DataType.getArray(PositionDataType.INSTANCE))) {

        } else if (isArrayOfSimpleStruct(targetField)) {
            considerNestedFields(builder, field);
        } else if (isMapOfSimpleStruct(targetField)) {
            considerSimpleField(builder, field.getNestedField("key"));
            considerNestedFields(builder, field.getNestedField("value"));
        } else if (isMapOfPrimitiveType(targetField)) {
            considerSimpleField(builder, field.getNestedField("key"));
            considerSimpleField(builder, field.getNestedField("value"));
        }
    }

    private static void considerNestedFields(ImportedFieldsConfig.Builder builder, ImportedField field) {
        if (field instanceof ImportedComplexField) {
            ImportedComplexField complexField = (ImportedComplexField) field;
            complexField.getNestedFields().forEach(nestedField -> considerSimpleField(builder, nestedField));
        }
    }

    private static void considerSimpleField(ImportedFieldsConfig.Builder builder, ImportedField field) {
        ImmutableSDField targetField = field.targetField();
        String targetFieldName = targetField.getName();
        if (!isNestedFieldName(targetFieldName)) {
            if (targetField.doesAttributing()) {
                builder.attribute.add(createAttributeBuilder(field));
            }
        } else {
            Attribute attribute = targetField.getAttribute();
            if (attribute != null) {
                builder.attribute.add(createAttributeBuilder(field));
            }
        }
    }

    private static ImportedFieldsConfig.Attribute.Builder createAttributeBuilder(ImportedField field) {
        ImportedFieldsConfig.Attribute.Builder result = new ImportedFieldsConfig.Attribute.Builder();
        result.name(field.fieldName());
        result.referencefield(field.reference().referenceField().getName());
        result.targetfield(field.targetField().getName());
        return result;
    }

}
