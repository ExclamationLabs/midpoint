/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.security.enforcer.impl.prism;

import static com.evolveum.midpoint.security.enforcer.impl.prism.PrismEntityCoverage.*;

import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.evolveum.midpoint.prism.PrismContainerValue;
import com.evolveum.midpoint.prism.PrismValue;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.path.NameKeyedMap;
import com.evolveum.midpoint.prism.path.PathSet;
import com.evolveum.midpoint.security.enforcer.impl.AuthorizationEvaluation;
import com.evolveum.midpoint.util.DebugUtil;
import com.evolveum.midpoint.util.exception.*;

/**
 * Informs whether given {@link PrismValue} and its contained sub-items are covered in the specified context.
 *
 * (The context can be e.g. items/values that are allowed or denied by a particular operation.)
 *
 * @see PrismItemCoverageInformation
 */
class PrismValueCoverageInformation implements PrismEntityCoverageInformation {

    /** Match information for specified sub-items. */
    @NotNull private final NameKeyedMap<ItemName, PrismItemCoverageInformation> itemsMap = new NameKeyedMap<>();

    /**
     * - If `true`, then items that are not mentioned are considered as {@link PrismEntityCoverage#NONE}
     * (i.e., what is not mentioned, is not covered).
     * - If `false` they are considered as {@link PrismEntityCoverage#FULL}
     * (i.e., what is not mentioned, is covered in full).
     */
    private boolean positive;

    private PrismValueCoverageInformation(boolean positive) {
        this.positive = positive;
    }

    static PrismValueCoverageInformation fullCoverage() {
        return new PrismValueCoverageInformation(false);
    }

    static PrismValueCoverageInformation noCoverage() {
        return new PrismValueCoverageInformation(true);
    }

    public @NotNull PrismEntityCoverage getCoverage() {
        if (itemsMap.isEmpty()) {
            return positive ? NONE : FULL;
        } else {
            // We do not know if the object really contains something of interest. It may or may not.
            return PARTIAL;
        }
    }

    private boolean isPositive() {
        return positive;
    }

    @NotNull PrismItemCoverageInformation getItemCoverageInformation(@NotNull ItemName name) {
        var forItem = itemsMap.get(name);
        if (forItem != null) {
            return forItem;
        }
        return positive ? PrismItemCoverageInformation.noCoverage() : PrismItemCoverageInformation.fullCoverage();
    }

    /** Returns `null` if the authorization is irrelevant for the current object. */
    static @Nullable PrismValueCoverageInformation forAuthorization(
            PrismValue value, @NotNull AuthorizationEvaluation evaluation)
            throws ConfigurationException, SchemaException, ExpressionEvaluationException, CommunicationException,
            SecurityViolationException, ObjectNotFoundException {

        Collection<SelectorChainSegment> roots = SelectorChainSegment.createSelectorChains(value, evaluation);
        if (!roots.isEmpty()) {
            PrismValueCoverageInformation merged = PrismValueCoverageInformation.noCoverage();
            for (SelectorChainSegment root : roots) {
                merged.merge(
                        forSegment(value, value, root, evaluation));
            }
            return merged;
        } else {
            return null;
        }
    }

    private static PrismValueCoverageInformation forSegment(
            @NotNull PrismValue value,
            @NotNull PrismValue rootValue,
            @NotNull SelectorChainSegment segment,
            @NotNull AuthorizationEvaluation evaluation) throws ConfigurationException, SchemaException,
            ExpressionEvaluationException, CommunicationException, SecurityViolationException, ObjectNotFoundException {

        if (!evaluation.isSelectorApplicable(segment.selector, value, Set.of(), "TODO")) {
            return PrismValueCoverageInformation.noCoverage();
        }

        if (!segment.positives.isEmpty() || segment.next != null) {
            var coverage = forPositivePaths(segment.positives);
            if (segment.next != null) {
                coverage.merge(forNextSegment(segment.path, segment.next, value, rootValue, evaluation));
            }
            return coverage;
        } else {
            return forNegativePaths(segment.negatives);
        }
    }

    private static PrismValueCoverageInformation forNextSegment(
            ItemPath path,
            SelectorChainSegment nextSegment,
            PrismValue parentValue,
            PrismValue rootValue,
            AuthorizationEvaluation evaluation)
            throws ConfigurationException, SchemaException, ExpressionEvaluationException, CommunicationException,
            SecurityViolationException, ObjectNotFoundException {
        if (!(parentValue instanceof PrismContainerValue<?>)) {
            return PrismValueCoverageInformation.noCoverage();
        }
        var pcv = (PrismContainerValue<?>) parentValue;
        var item = pcv.findItem(path);
        if (item == null) {
            // Item is not present in the PCV, the coverage needs no update.
            return PrismValueCoverageInformation.noCoverage();
        }

        var root = PrismValueCoverageInformation.noCoverage();
        var itemCoverageInformation = createItemCoverageInformationObject(root, path); // TODO evaluate lazily

        for (PrismValue itemValue : item.getValues()) {
            PrismValueCoverageInformation subValueCoverage =
                    forSegment(itemValue, rootValue, nextSegment, evaluation);
            if (subValueCoverage.getCoverage() != NONE) {
                itemCoverageInformation.addForValue(itemValue, subValueCoverage);
            }
        }
        if (itemCoverageInformation.getCoverage() == NONE) {
            return PrismValueCoverageInformation.noCoverage(); // to avoid useless root->item chain
        } else {
            return root;
        }
    }

    // TODO explain
    private static PrismItemCoverageInformation createItemCoverageInformationObject(
            PrismValueCoverageInformation root, ItemPath path) {
        var current = root;
        PrismItemCoverageInformation last = null;
        List<?> segments = path.getSegments();
        assert !segments.isEmpty();
        for (Object segment : segments) {
            ItemName name = ItemPath.toName(segment);
            PrismValueCoverageInformation next = PrismValueCoverageInformation.noCoverage();
            last = PrismItemCoverageInformation.single(next);
            current.itemsMap.put(name, last);
            current = next;
        }
        return last;
    }

    private static PrismValueCoverageInformation forPositivePaths(PathSet positives) {
        if (positives.contains(ItemPath.EMPTY_PATH)) {
            return fullCoverage();
        }
        var coverage = noCoverage();
        for (Map.Entry<ItemName, PathSet> entry : positives.factor().entrySet()) {
            ItemName first = entry.getKey();
            PathSet rests = entry.getValue();
            coverage.itemsMap.put(
                    first,
                    PrismItemCoverageInformation.single(
                            forPositivePaths(rests)));
        }
        return coverage;
    }

    private static PrismValueCoverageInformation forNegativePaths(PathSet negatives) {
        if (negatives.contains(ItemPath.EMPTY_PATH)) {
            // "except for" item means that all its sub-items are excluded
            return noCoverage();
        }
        var coverage = fullCoverage();
        for (Map.Entry<ItemName, PathSet> entry : negatives.factor().entrySet()) {
            ItemName first = entry.getKey();
            PathSet rests = entry.getValue();
            coverage.itemsMap.put(
                    first,
                    PrismItemCoverageInformation.single(
                            forNegativePaths(rests)));
        }
        return coverage;
    }

    public void merge(@NotNull PrismValueCoverageInformation increment) {
        if (isPositive()) {
            if (increment.isPositive()) {
                mergePositiveIntoPositive(increment);
            } else {
                mergeNegativeIntoPositive(increment);
            }
        } else {
            if (increment.isPositive()) {
                mergePositiveIntoNegative(increment);
            } else {
                mergeNegativeIntoNegative(increment);
            }
        }
    }

    private void mergePositiveIntoPositive(PrismValueCoverageInformation increment) {
        for (var iEntry : increment.itemsMap.entrySet()) {
            ItemName iFirst = iEntry.getKey();
            PrismItemCoverageInformation iCoverage = iEntry.getValue();
            itemsMap
                    .computeIfAbsent(iFirst, k -> PrismItemCoverageInformation.noCoverage())
                    .merge(iCoverage);
        }
    }

    private void mergePositiveIntoNegative(PrismValueCoverageInformation increment) {
        for (var iEntry : increment.itemsMap.entrySet()) {
            ItemName iFirst = iEntry.getKey();
            PrismItemCoverageInformation iCoverage = iEntry.getValue();
            PrismItemCoverageInformation eCoverage = itemsMap.get(iFirst);
            if (eCoverage == null) {
                // New item is not mentioned in existing -> it is already fully covered there (so let's ignore it)
            } else {
                eCoverage.merge(iCoverage);
            }
        }
    }

    private void mergeNegativeIntoPositive(PrismValueCoverageInformation increment) {

        // Transposing to "merge positive into negative": this -> copy (positive), increment -> this (negative)

        PrismValueCoverageInformation copy = new PrismValueCoverageInformation(this.positive);
        copy.itemsMap.putAll(this.itemsMap);
        assert copy.isPositive();

        this.positive = increment.positive;
        this.itemsMap.clear();
        this.itemsMap.putAll(increment.itemsMap);
        assert !this.isPositive();

        mergePositiveIntoNegative(copy);
    }

    private void mergeNegativeIntoNegative(PrismValueCoverageInformation increment) {
        // We preserve only items declared in both coverages.
        // (The ones mentioned on only one side are fully covered by the other side!)
        for (var eEntry : List.copyOf(itemsMap.entrySet())) {
            ItemName eFirst = eEntry.getKey();
            PrismItemCoverageInformation eCoverage = eEntry.getValue();
            PrismItemCoverageInformation iCoverage = increment.itemsMap.get(eFirst);
            if (iCoverage == null) {
                itemsMap.remove(eFirst);
            } else {
                eCoverage.merge(iCoverage);
            }
        }
    }

    @Override
    public String debugDump(int indent) {
        boolean empty = itemsMap.isEmpty();
        String label;
        if (empty) {
            label = positive ? "NO COVERAGE" : "FULL COVERAGE";
        } else {
            label = positive ? "DEFAULT: NO COVERAGE" : "DEFAULT: FULL COVERAGE (all-except-for)";
        }
        var sb = DebugUtil.createTitleStringBuilder(
                String.format("Prism value coverage information [%s]: %s\n",
                        getClass().getSimpleName(),
                        label),
                indent);
        if (!empty) {
            DebugUtil.debugDumpWithLabel(sb, "Individual sub-items", itemsMap, indent + 1);
        }
        return sb.toString();
    }
}
