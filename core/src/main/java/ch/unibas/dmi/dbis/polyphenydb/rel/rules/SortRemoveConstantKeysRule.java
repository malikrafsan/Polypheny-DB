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

package ch.unibas.dmi.dbis.polyphenydb.rel.rules;


import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptPredicateList;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptRule;
import ch.unibas.dmi.dbis.polyphenydb.plan.RelOptRuleCall;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelCollationTraitDef;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelCollations;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelFieldCollation;
import ch.unibas.dmi.dbis.polyphenydb.rel.RelNode;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.RelFactories;
import ch.unibas.dmi.dbis.polyphenydb.rel.core.Sort;
import ch.unibas.dmi.dbis.polyphenydb.rel.metadata.RelMetadataQuery;
import ch.unibas.dmi.dbis.polyphenydb.rex.RexBuilder;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Planner rule that removes keys from a a {@link Sort} if those keys are known to be constant, or removes the entire Sort if all keys are constant.
 *
 * Requires {@link RelCollationTraitDef}.
 */
public class SortRemoveConstantKeysRule extends RelOptRule {

    public static final SortRemoveConstantKeysRule INSTANCE = new SortRemoveConstantKeysRule();


    private SortRemoveConstantKeysRule() {
        super(
                operand( Sort.class, any() ),
                RelFactories.LOGICAL_BUILDER,
                "SortRemoveConstantKeysRule" );
    }


    @Override
    public void onMatch( RelOptRuleCall call ) {
        final Sort sort = call.rel( 0 );
        final RelMetadataQuery mq = call.getMetadataQuery();
        final RelNode input = sort.getInput();
        final RelOptPredicateList predicates = mq.getPulledUpPredicates( input );
        if ( predicates == null ) {
            return;
        }

        final RexBuilder rexBuilder = sort.getCluster().getRexBuilder();
        final List<RelFieldCollation> collationsList =
                sort.getCollation().getFieldCollations().stream()
                        .filter( fc -> !predicates.constantMap.containsKey( rexBuilder.makeInputRef( input, fc.getFieldIndex() ) ) )
                        .collect( Collectors.toList() );

        if ( collationsList.size() == sort.collation.getFieldCollations().size() ) {
            return;
        }

        // No active collations. Remove the sort completely
        if ( collationsList.isEmpty() && sort.offset == null && sort.fetch == null ) {
            call.transformTo( input );
            call.getPlanner().setImportance( sort, 0.0 );
            return;
        }

        final Sort result = sort.copy( sort.getTraitSet(), input, RelCollations.of( collationsList ) );
        call.transformTo( result );
        call.getPlanner().setImportance( sort, 0.0 );
    }
}
