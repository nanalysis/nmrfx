NMRFXS='../../../target/nmrfx-stru*-bin/nmrfx-structure*/nmrfxs'
echo $NMRFXS
$NMRFXS predict -o star -r attr 2KOC.pdb
$NMRFXS predict -o starH -r attr 2KOC.pdb
$NMRFXS predict -o attr -r attr 2KOC.pdb
$NMRFXS predict -o star -r dist 2KOC.pdb
$NMRFXS predict -o star -r rc 2KOC.pdb
$NMRFXS predict -o star 1d3z.pdb
$NMRFXS predict -o protein 1d3z.pdb
