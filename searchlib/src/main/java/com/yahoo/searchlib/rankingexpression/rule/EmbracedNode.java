// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * This class represents another expression enclosed in braces.
 *
 * @author Simon Thoresen Hult
 */
public final class EmbracedNode extends CompositeNode {

    // The node to embrace.
    private final ExpressionNode value;

    /**
     * Creates a new expression node that embraces another.
     *
     * @param value The node to embrace.
     */
    public EmbracedNode(ExpressionNode value) {
        this.value=value;
    }

    /** Returns the node enclosed by this */
    public ExpressionNode getValue() { return value; }

    @Override
    public List<ExpressionNode> children() {
        return Collections.singletonList(value);
    }

    @Override
    public StringBuilder toString(StringBuilder string, SerializationContext context, Deque<String> path, CompositeNode parent) {
        return value.toString(string.append('('), context, path, this).append(')');
    }

    @Override
    public Value evaluate(Context context) {
        return value.evaluate(context);
    }

    @Override
    public TensorType type(TypeContext<Reference> context) {
        return value.type(context);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> newChildren) {
        if (newChildren.size() != 1)
            throw new IllegalArgumentException("Expected 1 child but got " + newChildren.size());
        return new EmbracedNode(newChildren.get(0));
    }

}
