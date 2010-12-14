/**
 * <p>
 * Copyright (c) 2008 The Regents of the University of California<br>
 * All rights reserved.
 * </p>
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 * </p>
 * <ul>
 * <li>Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.</li>
 * <li>Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.</li>
 * <li>Neither the name of the author nor the names of any contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.</li>
 * </ul>
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * </p>
 */

package ex;

import static ex.MergeSortTest.LINE_LENGTH;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.dapper.codelet.Codelet;
import org.dapper.codelet.Resource;
import org.shared.codec.Codecs;
import org.shared.util.Arithmetic;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A member of {@link MergeSortTest} that creates a file for sorting.
 * 
 * @author Roy Liu
 */
public class CreateSortFile implements Codelet {

    @Override
    public void run(List<Resource> inResources, List<Resource> outResources, Node parameters) throws IOException {

        NodeList children = parameters.getChildNodes();

        File file = new File(children.item(0).getTextContent());
        int nLines = Integer.parseInt(children.item(1).getTextContent());

        PrintStream ps = new PrintStream(file);

        for (int i = 0; i < nLines; i++) {

            ps.printf(Codecs.bytesToHex(Arithmetic.nextBytes(LINE_LENGTH >>> 1)) //
                    .substring(0, LINE_LENGTH - 1));
            ps.printf("%n");
        }
    }

    /**
     * Default constructor.
     */
    public CreateSortFile() {
    }

    /**
     * Creates a human-readable description of this {@link Codelet}.
     */
    @Override
    public String toString() {
        return "Create";
    }
}
