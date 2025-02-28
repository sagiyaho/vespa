// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

final class DataValue extends Value {
    private final byte[] value;
    private DataValue(byte[] value) { this.value = value; }
    public static Value create(byte[] value) {
        if (value == null) {
            return NixValue.instance();
        } else {
            return new DataValue(value);
        }
    }
    public final Type type() { return Type.DATA; }
    public final byte[] asData() { return this.value; }
    public final void accept(Visitor v) { v.visitData(value); }
}
