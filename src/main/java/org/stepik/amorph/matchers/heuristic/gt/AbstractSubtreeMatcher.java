/*
 * MIT License
 *
 * Copyright (c) 2017 Nikita Lapkov
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
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

package org.stepik.amorph.matchers.heuristic.gt;

import org.stepik.amorph.matchers.*;
import org.stepik.amorph.tree.ITree;
import org.stepik.amorph.matchers.MappingStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class AbstractSubtreeMatcher extends Matcher {

    public static int MIN_HEIGHT = Integer.parseInt(System.getProperty("gt.stm.mh", "2"));

    public AbstractSubtreeMatcher(ITree src, ITree dst, MappingStore store) {
        super(src, dst, store);
    }

    private void popLarger(PriorityTreeList srcTrees, PriorityTreeList dstTrees) {
        if (srcTrees.peekHeight() > dstTrees.peekHeight())
            srcTrees.open();
        else
            dstTrees.open();
    }

    public void match() {
        MultiMappingStore multiMappings = new MultiMappingStore();

        PriorityTreeList srcTrees = new PriorityTreeList(src);
        PriorityTreeList dstTrees = new PriorityTreeList(dst);

        while (srcTrees.peekHeight() != -1 && dstTrees.peekHeight() != -1) {
            while (srcTrees.peekHeight() != dstTrees.peekHeight())
                popLarger(srcTrees, dstTrees);

            List<ITree> currentHeightSrcTrees = srcTrees.pop();
            List<ITree> currentHeightDstTrees = dstTrees.pop();

            boolean[] marksForSrcTrees = new boolean[currentHeightSrcTrees.size()];
            boolean[] marksForDstTrees = new boolean[currentHeightDstTrees.size()];

            for (int i = 0; i < currentHeightSrcTrees.size(); i++) {
                for (int j = 0; j < currentHeightDstTrees.size(); j++) {
                    ITree src = currentHeightSrcTrees.get(i);
                    ITree dst = currentHeightDstTrees.get(j);

                    if (src.isIsomorphicTo(dst)) {
                        multiMappings.link(src, dst);
                        marksForSrcTrees[i] = true;
                        marksForDstTrees[j] = true;
                    }
                }
            }

            for (int i = 0; i < marksForSrcTrees.length; i++)
                if (marksForSrcTrees[i] == false)
                    srcTrees.open(currentHeightSrcTrees.get(i));
            for (int j = 0; j < marksForDstTrees.length; j++)
                if (marksForDstTrees[j] == false)
                    dstTrees.open(currentHeightDstTrees.get(j));
            srcTrees.updateHeight();
            dstTrees.updateHeight();
        }

        filterMappings(multiMappings);
    }

    public abstract void filterMappings(MultiMappingStore multiMappings);

    protected double sim(ITree src, ITree dst) {
        double jaccard = jaccardSimilarity(src.getParent(), dst.getParent());
        int posSrc = (src.isRoot()) ? 0 : src.getParent().getChildPosition(src);
        int posDst = (dst.isRoot()) ? 0 : dst.getParent().getChildPosition(dst);
        int maxSrcPos =  (src.isRoot()) ? 1 : src.getParent().getChildren().size();
        int maxDstPos =  (dst.isRoot()) ? 1 : dst.getParent().getChildren().size();
        int maxPosDiff = Math.max(maxSrcPos, maxDstPos);
        double pos = 1D - ((double) Math.abs(posSrc - posDst) / (double) maxPosDiff);
        double po = 1D - ((double) Math.abs(src.getId() - dst.getId()) / (double) this.getMaxTreeSize());
        return 100 * jaccard + 10 * pos + po;
    }

    protected int getMaxTreeSize() {
        return Math.max(src.getSize(), dst.getSize());
    }

    protected void retainBestMapping(List<Mapping> mappings, Set<ITree> srcIgnored, Set<ITree> dstIgnored) {
        while (mappings.size() > 0) {
            Mapping mapping = mappings.remove(0);
            if (!(srcIgnored.contains(mapping.getFirst()) || dstIgnored.contains(mapping.getSecond()))) {
                addMappingRecursively(mapping.getFirst(), mapping.getSecond());
                srcIgnored.add(mapping.getFirst());
                dstIgnored.add(mapping.getSecond());
            }
        }
    }

    private static class PriorityTreeList {

        private List<ITree>[] trees;

        private int maxHeight;

        private int currentIdx;

        @SuppressWarnings("unchecked")
        public PriorityTreeList(ITree tree) {
            int listSize = tree.getHeight() - MIN_HEIGHT + 1;
            if (listSize < 0)
                listSize = 0;
            if (listSize == 0)
                currentIdx = -1;
            trees = (List<ITree>[]) new ArrayList[listSize];
            maxHeight = tree.getHeight();
            addTree(tree);
        }

        private int idx(ITree tree) {
            return idx(tree.getHeight());
        }

        private int idx(int height) {
            return maxHeight - height;
        }

        private int height(int idx) {
            return maxHeight - idx;
        }

        private void addTree(ITree tree) {
            if (tree.getHeight() >= MIN_HEIGHT) {
                int idx = idx(tree);
                if (trees[idx] == null) trees[idx] = new ArrayList<>();
                trees[idx].add(tree);
            }
        }

        public List<ITree> open() {
            List<ITree> pop = pop();
            if (pop != null) {
                for (ITree tree: pop) open(tree);
                updateHeight();
                return pop;
            } else return null;
        }

        public List<ITree> pop() {
            if (currentIdx == -1)
                return null;
            else {
                List<ITree> pop = trees[currentIdx];
                trees[currentIdx] = null;
                return pop;
            }
        }

        public void open(ITree tree) {
            for (ITree c: tree.getChildren()) addTree(c);
        }

        public int peekHeight() {
            return (currentIdx == -1) ? -1 : height(currentIdx);
        }

        public void updateHeight() {
            currentIdx = -1;
            for (int i = 0; i < trees.length; i++) {
                if (trees[i] != null) {
                    currentIdx = i;
                    break;
                }
            }
        }
    }
}
