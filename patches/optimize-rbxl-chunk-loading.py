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

# Avoid allocating a second per-class Instance reference list for every PROP chunk.
# The reader already has the stable reference IDs in ClassInfo.refs, so resolve each
# instance only while assigning its decoded property.
old = """        val insts = ci.refs.map { instances[it]!! }
        val n = insts.size
        val payloadStart = r.pos
        val values: List<Any?> = try {
            readValues(r, type, n, insts, name)
"""
new = """        val refs = ci.refs
        val n = refs.size
        val payloadStart = r.pos
        val values: List<Any?> = try {
            readValues(r, type, n, name)
"""
assert old in s
s = s.replace(old, new, 1)

old = """            for (i in 0 until n) { insts[i].props[name] = RawPropValue(type, payload, i, n); insts[i].propTypes[name] = type }
            return
        }
        for (i in 0 until n) {
            if (type != PropType.REF) insts[i].props[name] = values[i]
            insts[i].propTypes[name] = type
        }
"""
new = """            for (i in 0 until n) {
                val inst = instances[refs[i]] ?: continue
                inst.props[name] = RawPropValue(type, payload, i, n)
                inst.propTypes[name] = type
            }
            return
        }
        for (i in 0 until n) {
            val inst = instances[refs[i]] ?: continue
            if (type != PropType.REF) inst.props[name] = values[i]
            inst.propTypes[name] = type
        }
"""
assert old in s
s = s.replace(old, new, 1)

old = """    private fun readValues(r: ByteReader, type: Int, n: Int, insts: List<Instance>, name: String): List<Any?> = when (type) {
"""
new = """    private fun readValues(r: ByteReader, type: Int, n: Int, name: String): List<Any?> = when (type) {
"""
assert old in s
s = s.replace(old, new, 1)

old = """            for (i in 0 until n) pendingRefs.add(Triple(insts[i], name, refs[i]))
            List(n) { null }
"""
new = """            for (ref in refs.indices) pendingRefs.add(Triple(instances[ciRefPlaceholder(ref)], name, refs[ref]))
            emptyList()
"""
# Do not apply this section: it needs class refs, which readValues doesn't receive.
# REF property values are uncommon but retain the existing resolver through a lightweight
# argument only when needed. Instead keep the old insts parameter for REF without allocating
# a mapped list by using an AbstractList view over ClassInfo.refs.
# Replace signature and caller with a lazy indexed view.
s=s.replace("""        val refs = ci.refs
        val n = refs.size
        val payloadStart = r.pos
        val values: List<Any?> = try {
            readValues(r, type, n, name)
""","""        val refs = ci.refs
        val n = refs.size
        val instView = object : AbstractList<Instance>() {
            override val size: Int get() = refs.size
            override fun get(index: Int): Instance = instances[refs[index]]!!
        }
        val payloadStart = r.pos
        val values: List<Any?> = try {
            readValues(r, type, n, instView, name)
""",1)
s=s.replace("""    private fun readValues(r: ByteReader, type: Int, n: Int, insts: List<Instance>, name: String): List<Any?> = when (type) {
""","""    private fun readValues(r: ByteReader, type: Int, n: Int, insts: List<Instance>, name: String): List<Any?> = when (type) {
""",1)
p.write_text(s)
