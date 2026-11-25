# 0.3.DL custom models

SA-MP 0.3.DL lets a server ship its own skins and objects. Two lines in `models/artconfig.txt` do
the work:

```
AddCharModel(280, 25000, "cop.dff", "cop.txd");
AddSimpleModel(-1, 19379, -2000, "cop.dff", "cop.txd");
```

`AddCharModel(baseid, newid, dff, txd)` registers a skin: `baseid` is a stock skin (0 to 311) whose
animation and voice the new one borrows, `newid` must be 20000 to 30000. `AddSimpleModel(vworld,
baseid, newid, dff, txd)` registers an object: `vworld` is the virtual world or -1 for all of them,
`baseid` is a stock object whose collision is inherited, `newid` must be -30000 to -1000. The client
then downloads the files and keeps them in its cache folder.

## What this client does today

`bin/artconfig.py` reads and checks a server's `artconfig.txt`:

```bash
bin/artconfig.py models/artconfig.txt --models models --check
bin/artconfig.py models/artconfig.txt --models models --manifest models.json
```

It refuses the mistakes a server accepts at startup and then loses a model over:

| Refused | Why it matters |
|---|---|
| skin id outside 20000..30000 | the server registers it and the skin never loads |
| object id outside -30000..-1000 | same, on the object side |
| base id outside 0..311 for a skin | only stock skins exist to inherit from |
| the same new id twice | the second registration silently loses |
| a listed file missing from `models/` | the download 404s for every player |
| `../` or a leading `/` in a name | the path would leave the models folder |
| a line without the trailing `;` | the server's parser skips it |

`--manifest` writes the inventory a cache needs: one entry per model with `sha256` and size for both
files, and a `total_bytes` counted per unique file, not per entry, because several skins usually
share one texture.

`bin/self-test.sh artconfig` proves the good case and every refusal above with a fixture, so the
tool cannot quietly start accepting a broken file.

## What is not implemented, and why

The client cannot draw these models yet. Nothing in the engine this client is built on knows about
`artconfig.txt`, custom model ids or the 0.3.DL download handshake, and the SA-MP client that does
know is not ours to copy.

That leaves three pieces of real work, in order:

1. A manifest and a cache folder layout. `bin/artconfig.py --manifest` is the first half of this and
   works now.
2. The transport: the client asks the server for the model files and verifies them against the
   manifest. This needs one real `artconfig.txt` plus its `.dff`/`.txd` files to test against, from a
   server you run or have permission to use.
3. The engine side: registering a downloaded model with the game so a server can put it on a player
   or in the world. That work lives in the native layer and cannot be tested without 1 and 2.

Until those are done, a client that joins a 0.3.DL server with custom models shows the base models
instead: the server's ids resolve to nothing and the stock object or skin stays visible. Nothing
crashes, and no download happens.

## If you run a 0.3.DL server

`bin/artconfig.py --check` is a pre-flight you can run before every deploy, in CI or by hand. It is
cheaper than discovering a missing texture from a player's screenshot.
