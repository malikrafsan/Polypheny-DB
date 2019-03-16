/*
 * This file is based on code taken from the Apache Calcite project, which was released under the Apache License.
 * The changes are released under the MIT license.
 *
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Databases and Information Systems Research Group, University of Basel, Switzerland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ch.unibas.dmi.dbis.polyphenydb.sql.fun;


import ch.unibas.dmi.dbis.polyphenydb.sql.SqlCall;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlFunction;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlFunctionCategory;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlKind;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlLiteral;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlNode;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlOperatorBinding;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlUtil;
import ch.unibas.dmi.dbis.polyphenydb.sql.SqlWriter;
import ch.unibas.dmi.dbis.polyphenydb.sql.parser.SqlParserPos;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.OperandTypes;
import ch.unibas.dmi.dbis.polyphenydb.sql.type.ReturnTypes;
import ch.unibas.dmi.dbis.polyphenydb.sql.validate.SqlMonotonicity;
import com.google.common.base.Preconditions;


/**
 * Definition of the "FLOOR" and "CEIL" built-in SQL functions.
 */
public class SqlFloorFunction extends SqlMonotonicUnaryFunction {


    public SqlFloorFunction( SqlKind kind ) {
        super( kind.name(),
                kind,
                ReturnTypes.ARG0_OR_EXACT_NO_SCALE,
                null,
                OperandTypes.or(
                        OperandTypes.NUMERIC_OR_INTERVAL,
                        OperandTypes.sequence(
                                "'" + kind + "(<DATE> TO <TIME_UNIT>)'\n"
                                        + "'" + kind + "(<TIME> TO <TIME_UNIT>)'\n"
                                        + "'" + kind + "(<TIMESTAMP> TO <TIME_UNIT>)'",
                                OperandTypes.DATETIME,
                                OperandTypes.ANY ) ),
                SqlFunctionCategory.NUMERIC );
        Preconditions.checkArgument( kind == SqlKind.FLOOR || kind == SqlKind.CEIL );
    }


    @Override
    public SqlMonotonicity getMonotonicity( SqlOperatorBinding call ) {
        // Monotonic iff its first argument is, but not strict.
        return call.getOperandMonotonicity( 0 ).unstrict();
    }


    @Override
    public void unparse( SqlWriter writer, SqlCall call, int leftPrec, int rightPrec ) {
        final SqlWriter.Frame frame = writer.startFunCall( getName() );
        if ( call.operandCount() == 2 ) {
            call.operand( 0 ).unparse( writer, 0, 100 );
            writer.sep( "TO" );
            call.operand( 1 ).unparse( writer, 100, 0 );
        } else {
            call.operand( 0 ).unparse( writer, 0, 0 );
        }
        writer.endFunCall( frame );
    }


    /**
     * Copies a {@link SqlCall}, replacing the time unit operand with the given literal.
     *
     * @param call Call
     * @param literal Literal to replace time unit with
     * @param pos Parser position
     * @return Modified call
     */
    public static SqlCall replaceTimeUnitOperand( SqlCall call, String literal, SqlParserPos pos ) {
        SqlLiteral literalNode = SqlLiteral.createCharString( literal, null, pos );
        return call.getOperator().createCall( call.getFunctionQuantifier(), pos, call.getOperandList().get( 0 ), literalNode );
    }


    /**
     * Most dialects that natively support datetime floor will use this.
     * In those cases the call will look like TRUNC(datetime, 'year').
     *
     * @param writer SqlWriter
     * @param call SqlCall
     * @param funName Name of the sql function to call
     * @param datetimeFirst Specify the order of the datetime &amp; timeUnit arguments
     */
    public static void unparseDatetimeFunction( SqlWriter writer, SqlCall call, String funName, Boolean datetimeFirst ) {
        SqlFunction func = new SqlFunction(
                funName,
                SqlKind.OTHER_FUNCTION,
                ReturnTypes.ARG0_NULLABLE_VARYING,
                null,
                null,
                SqlFunctionCategory.STRING );

        SqlCall call1;
        if ( datetimeFirst ) {
            call1 = call;
        } else {
            // switch order of operands
            SqlNode op1 = call.operand( 0 );
            SqlNode op2 = call.operand( 1 );

            call1 = call.getOperator().createCall( call.getParserPosition(), op2, op1 );
        }

        SqlUtil.unparseFunctionSyntax( func, writer, call1 );
    }
}