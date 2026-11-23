from pathlib import Path

p = Path("source/app/src/main/java/com/exampletest/mobilestudiotest/format/RbxBinary.kt")
s = p.read_text()

old = """    private class Chunk(val name: String, val data: ByteArray)
"""
new = """    private class Chunk(val name: String, val offset: Int, val compressedLength: Int, val uncompressedLength: Int)
"""
assert old in s
s = s.replace(old, new, 1)

old = """        val chunks = ArrayList<Chunk>()
        while (r.remaining() >= 16) {
            val name = String(r.bytes(4), Charsets.ISO_8859_1).trimEnd('\\u0000')
            val comp = r.i32(); val uncomp = r.i32(); r.i32()
            val body = if (comp == 0) r.bytes(uncomp) else {
                val start = r.pos
                r.bytes(comp)
                decompress(data, start, comp, uncomp)
            }
            chunks.add(Chunk(name, body))
            if (name == "END") break
        }
        for (c in chunks) if (c.name == "META") readMeta(c.data)
        for (c in chunks) if (c.name == "SSTR") readSstr(c.data)
        for (c in chunks) if (c.name == "INST") readInst(c.data)
        for (c in chunks) if (c.name == "PROP") {
            try { readProp(c.data) } catch (e: Exception) { warnings.add("Skipped a property: ${e.message}") }
        }
        for ((inst, prop, ref) in pendingRefs) inst.props[prop] = if (ref < 0) null else instances[ref]
        val roots = ArrayList<Instance>()
        for (c in chunks) if (c.name == "PRNT") readPrnt(c.data, roots)
        for (c in chunks) if (c.name !in setOf("META", "SSTR", "INST", "PROP", "PRNT", "END", "SIGN"))
            warnings.add("Ignored unknown chunk ${c.name}")
"""
new = """        // Index chunk locations only. Large compressed chunks (especially SSTR) are decoded
        // one at a time below instead of retaining every decompressed chunk simultaneously.
        val chunks = ArrayList<Chunk>()
        while (r.remaining() >= 16) {
            val name = String(r.bytes(4), Charsets.ISO_8859_1).trimEnd('\\u0000')
            val comp = r.i32(); val uncomp = r.i32(); r.i32()
            if (comp < 0 || uncomp < 0 || comp > r.remaining() || (comp == 0 && uncomp > r.remaining()))
                throw RbxFormatException("Invalid $name chunk length")
            val offset = r.pos
            chunks.add(Chunk(name, offset, comp, uncomp))
            r.pos += if (comp == 0) uncomp else comp
            if (name == "END") break
        }
        fun withChunk(c: Chunk, action: (ByteArray) -> Unit) {
            val body = if (c.compressedLength == 0) {
                data.copyOfRange(c.offset, c.offset + c.uncompressedLength)
            } else {
                decompress(data, c.offset, c.compressedLength, c.uncompressedLength)
            }
            action(body)
        }
        for (c in chunks) if (c.name == "META") withChunk(c) { readMeta(it) }
        for (c in chunks) if (c.name == "SSTR") withChunk(c) { readSstr(it) }
        for (c in chunks) if (c.name == "INST") withChunk(c) { readInst(it) }
        for (c in chunks) if (c.name == "PROP") {
            withChunk(c) {
                try { readProp(it) } catch (e: Exception) { warnings.add("Skipped a property: ${e.message}") }
            }
        }
        for ((inst, prop, ref) in pendingRefs) inst.props[prop] = if (ref < 0) null else instances[ref]
        val roots = ArrayList<Instance>()
        for (c in chunks) if (c.name == "PRNT") withChunk(c) { readPrnt(it, roots) }
        for (c in chunks) if (c.name !in setOf("META", "SSTR", "INST", "PROP", "PRNT", "END", "SIGN"))
            warnings.add("Ignored unknown chunk ${c.name}")
"""
assert old in s
s = s.replace(old, new, 1)
p.write_text(s)
