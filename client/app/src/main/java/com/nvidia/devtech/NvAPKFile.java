// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.nvidia.devtech;

import java.io.InputStream;

/**
 * One open asset stream plus the buffer the engine reads it through.
 *
 * The field names and types are the interface: the engine allocates and fills this object
 * from native code, so renaming a field breaks it without breaking the compiler.
 */
public class NvAPKFile {
    public int bufferSize;
    public byte[] data;
    public InputStream is;
    public int length;
    public int position;
}
