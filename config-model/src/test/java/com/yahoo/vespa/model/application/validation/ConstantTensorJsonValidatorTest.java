// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.tensor.TensorType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.Reader;
import java.io.StringReader;

import static com.yahoo.test.json.JsonTestHelper.inputJson;
import static com.yahoo.vespa.model.application.validation.ConstantTensorJsonValidator.InvalidConstantTensor;

public class ConstantTensorJsonValidatorTest {

    private static Reader inputJsonToReader(String... lines) {
        return new StringReader(inputJson(lines));
    }

    private static void validateTensorJson(TensorType tensorType, Reader jsonTensorReader) {
        new ConstantTensorJsonValidator().validate("dummy.json", tensorType, jsonTensorReader);
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void ensure_that_unbounded_tensor_works() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x[], y[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': '99999', 'y': '47' },",
                        "            'value': 9932.0",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    public void ensure_that_bounded_tensor_within_limits_works() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x[5], y[10])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': '3', 'y': '2' },",
                        "            'value': 2.0",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    public void ensure_that_multiple_cells_work() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x[], y[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': '3', 'y': '2' },",
                        "            'value': 2.0",
                        "        },",
                        "        {",
                        "            'address': { 'x': '2', 'y': '0' },",
                        "            'value': 45",
                        "        }",
                        "   ]",
                        "}"));
    }


    @Test
    public void ensure_that_no_cells_work() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x[], y[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': []",
                        "}"));
    }

    @Test
    public void ensure_that_bound_tensor_outside_limits_is_disallowed() {
        expectedException.expect(InvalidConstantTensor.class);
        expectedException.expectMessage("Index 5 not within limits of bound dimension 'x'");

        validateTensorJson(
                TensorType.fromSpec("tensor(x[5], y[10])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': '5', 'y': '2' },",
                        "            'value': 1e47",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    public void ensure_that_mapped_tensor_works() {
        validateTensorJson(
                TensorType.fromSpec("tensor(x{}, y{})"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': 'andrei', 'y': 'bjarne' },",
                        "            'value': 2.0",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    public void ensure_that_non_integer_strings_in_address_points_are_disallowed_unbound() {
        expectedException.expect(InvalidConstantTensor.class);
        expectedException.expectMessage("Index 'a' for dimension 'x' is not an integer");

        validateTensorJson(
                TensorType.fromSpec("tensor(x[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': 'a' },",
                        "            'value': 47.0",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    public void ensure_that_tensor_coordinates_are_strings() {
        expectedException.expect(InvalidConstantTensor.class);
        expectedException.expectMessage("Tensor label is not a string (VALUE_NUMBER_INT)");

        validateTensorJson(
                TensorType.fromSpec("tensor(x[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': 47 },",
                        "            'value': 33.0",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    public void ensure_that_non_integer_strings_in_address_points_are_disallowed_bounded() {
        expectedException.expect(InvalidConstantTensor.class);
        expectedException.expectMessage("Index 'a' for dimension 'x' is not an integer");

        validateTensorJson(
                TensorType.fromSpec("tensor(x[5])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': 'a' },",
                        "            'value': 41.0",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    public void ensure_that_missing_coordinates_fail() {
        expectedException.expect(InvalidConstantTensor.class);
        expectedException.expectMessage("Tensor address missing dimension(s) y, z");

        validateTensorJson(
                TensorType.fromSpec("tensor(x[], y[], z[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': '3' },",
                        "            'value': 99.3",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    public void ensure_that_non_number_values_are_disallowed() {
        expectedException.expect(InvalidConstantTensor.class);
        expectedException.expectMessage("Tensor value is not a number (VALUE_STRING)");

        validateTensorJson(
                TensorType.fromSpec("tensor(x[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': '3' },",
                        "            'value': 'fruit'",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    public void ensure_that_extra_dimensions_are_disallowed() {
        expectedException.expect(InvalidConstantTensor.class);
        expectedException.expectMessage("Tensor dimension 'z' does not exist");

        validateTensorJson(
                TensorType.fromSpec("tensor(x[], y[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': '3', 'y': '2', 'z': '4' },",
                        "            'value': 99.3",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    public void ensure_that_duplicate_dimensions_are_disallowed() {
        expectedException.expect(InvalidConstantTensor.class);
        expectedException.expectMessage("Duplicate tensor dimension 'y'");

        validateTensorJson(
                TensorType.fromSpec("tensor(x[], y[])"),
                inputJsonToReader(
                        "{",
                        "   'cells': [",
                        "        {",
                        "            'address': { 'x': '1', 'y': '2', 'y': '4' },",
                        "            'value': 88.1",
                        "        }",
                        "   ]",
                        "}"));
    }

    @Test
    public void ensure_that_invalid_json_fails() {
        expectedException.expect(InvalidConstantTensor.class);
        expectedException.expectMessage("Failed to parse JSON stream");

        validateTensorJson(
                TensorType.fromSpec("tensor(x[], y[])"),
                inputJsonToReader(
                        "{",
                        "    cells': [",
                        "        {",
                        "            'address': { 'x': '3' 'y': '2' }",
                        "            'value': 2.0",
                        "        }",
                        "   ",
                        "}"));
    }

    @Test
    public void ensure_that_invalid_json_not_in_tensor_format_fails() {
        expectedException.expect(InvalidConstantTensor.class);
        expectedException.expectMessage("Expected field name 'cells', got 'stats'");

        validateTensorJson(TensorType.fromSpec("tensor(x[], y[])"),
                inputJsonToReader(
                        "{",
                        "   'stats': {",
                        "       '\u30d1\u30fc\u30d7\u30eb\u30b4\u30e0\u88fd\u306e\u30a2\u30d2\u30eb\u306f\u79c1\u3092\u6bba\u3059\u305f\u3081\u306b\u671b\u3093\u3067\u3044\u307e\u3059': true,",
                        "       'points': 47",
                        "   }",
                        "}"));
    }

}
