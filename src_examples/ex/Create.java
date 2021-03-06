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

import java.util.List;

import org.dapper.codelet.Codelet;
import org.dapper.codelet.CodeletUtilities;
import org.dapper.codelet.OutputHandleResource;
import org.dapper.codelet.Resource;
import org.shared.array.ObjectArray;
import org.shared.util.Arithmetic;
import org.shared.util.Control;
import org.w3c.dom.Node;

/**
 * A {@link Codelet} that simulates creation of random numbers of file handles along {@link OutputHandleResource}s.
 * 
 * @author Roy Liu
 */
public class Create implements Codelet {

    @Override
    public void run(List<Resource> inResources, List<Resource> outResources, Node parameters) {

        Arithmetic.randomize();

        for (OutputHandleResource ohr : CodeletUtilities.filter(outResources, OutputHandleResource.class)) {

            String stem = CodeletUtilities.createStem();

            int nHandles = Arithmetic.nextInt(3) + 1;

            if (Arithmetic.nextInt(2) == 0) {

                for (int i = 0; i < nHandles; i++) {

                    String subidentifier = String.format("%s_%08x", stem, i);
                    ohr.put("file_".concat(subidentifier), subidentifier);
                }

            } else {

                ObjectArray<String> newEntries = new ObjectArray<String>(String.class, nHandles, 2);

                for (int i = 0; i < nHandles; i++) {

                    String subidentifier = String.format("%s_%08x", stem, i);
                    newEntries.set("file_".concat(subidentifier), i, 0);
                    newEntries.set(subidentifier, i, 1);
                }

                ohr.put(newEntries);
            }
        }

        Control.sleep(Arithmetic.nextInt(2000));
    }

    /**
     * Default constructor.
     */
    public Create() {
    }

    /**
     * Creates a human-readable description of this {@link Codelet}.
     */
    @Override
    public String toString() {
        return "Create";
    }
}
