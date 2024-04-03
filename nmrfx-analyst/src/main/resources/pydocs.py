from pyproc import getDocs
from pyproc import getRefDocs

def genCmdDocs():
    docOutput = ''
    docs=getDocs()
    len = docs.size()
    for i in range(0,len,4):
        doc = docs.get(i+3) 
        cmd = docs.get(i) 
        docOutput += '<p>\n'
        docOutput += '<b>'+cmd+'</b>'+' '+''+doc+'\n'
        docOutput += '</p>\n'
        pars = docs.get(i+2) 
        docOutput += '<ul>\n'
        for par in pars:
             docOutput += '<li>\n'
             docOutput += '<b>'+par.get('name')+'</b>'+' '+par.get('desc')+'\n'
             docOutput += '<ul>\n'
             for elem in ('default','min','max','optional'):
                 if par.containsKey(elem):
                     docOutput += '<li>\n'
                     docOutput += '<b>'+elem+'</b>'+' '+str(par.get(elem))+'\n'
                     docOutput += '</li>\n'
             docOutput += '</ul>\n'
             docOutput += '</li>\n'
        docOutput += '</ul>\n'

    return docOutput

def genCmdDocsInMarkDown():
    docOutput = ''
    docs=getDocs()
    len = docs.size()
    for i in range(0,len,4):
        doc = docs.get(i+3)
        cmd = docs.get(i)
        docOutput += '\n'
        docOutput += '**'+cmd+'**'+' '+''+'\n\n'+doc+'\n'
        docOutput += '\n'
        example = docs.get(i+1)
        pars = docs.get(i+2)
        docOutput += '\n'
        docOutput += '    '+example+'\n'
        docOutput += '\n'
        for par in pars:
             docOutput += '* '
             docOutput += '**'+par.get('name')+'**\n'
             docOutput += par.get('desc')+'\n'
             for elem in ('default','min','max','optional'):
                 if par.containsKey(elem):
                     docOutput += '    * '
                     docOutput += '*'+elem+'*'+' '+str(par.get(elem))+'\n'

    return docOutput

def genRefDocsInMarkDown(ops):
    docOutput = ''
    docs=getRefDocs(ops)
    len = docs.size()
    for i in range(0,len,4):
        doc = docs.get(i+3)
        cmd = docs.get(i)
        docOutput += '\n'
        docOutput += '**'+cmd+'**'+' '+''+'\n\n'+doc+'\n'
        docOutput += '\n'
        example = docs.get(i+1)
        pars = docs.get(i+2)
        docOutput += '\n'
        docOutput += '    '+example+'\n'
        docOutput += '\n'
        for par in pars:
             docOutput += '* '
             docOutput += '**'+par.get('name')+'**\n'
             docOutput += par.get('desc')+'\n'
             for elem in ('default','min','max','optional'):
                 if par.containsKey(elem):
                     docOutput += '    * '
                     docOutput += '*'+elem+'*'+' '+str(par.get(elem))+'\n'

    return docOutput


def genRefDocs(ops):
    docOutput = ''
    docs=getRefDocs(ops)
    len = docs.size()
    for i in range(0,len,4):
        doc = docs.get(i+3) 
        cmd = docs.get(i) 
        example = docs.get(i+1)
        docOutput += '<p>'
        docOutput += '<b>'+cmd+'</b>'+' '+''+doc+''
        docOutput += '</p>'
        docOutput += '<p>'
        docOutput += example
        docOutput += '</p>'
        pars = docs.get(i+2) 
        docOutput += '<ul>'
        for par in pars:
             docOutput += '<li>'
             docOutput += '<b>'+par.get('name')+'</b>'+' '+par.get('desc')
             docOutput += '<ul>'
             for elem in ('default','min','max','optional'):
                 if par.containsKey(elem):
                     docOutput += '<li>'
                     docOutput += '<b>'+elem+'</b>'+' '+str(par.get(elem))
                     docOutput += '</li>'
             docOutput += '</ul>'
             docOutput += '</li>'
        docOutput += '</ul>'
    
    return docOutput

docsDescript = {}
docsDescript['file'] = '''
The File commands are used at the beginning of each script to specify the raw NMR file (the FID) to open, and the NMRView dataset to create.
'''

docsDescript['ref'] = '''
Referencing commands are used at the beginning of each script to set the sweep width, spectrometer frequency, referencing and dataset axis labels.  The commands can take specific values that override anything in the FIDs parameter files, or they can be the names of parameters that are found in the vendors parameter files.  Bruker parameters are specified with the name, a comma, and a dimension number.  So, for example, "SFO1,1" would be the spectrometer frequency found in the "acqus" file, and "SFO1,2" would be the spectrometer frequency found in the "acqu2s" file.  The command "p" can be used to get the value of a parameter.  For example, p('sw2')/2.0  would get the value of the "sw2" parameter and divide it by 2.0.
'''

docsDescript['proc'] = '''
The actual sequence of processing operations is automatically parallelized and run on multiple processing cores.  For example, a MacBook Pro might have an Intel i7 processor with 4 cores.  Each of those cores is "hyperthreaded" so in total the CPU appears to the operating system (and the Java code of NMRFx) as if it can run 8 simultaneous operations.  The sequence of operations would therefore be replicated 8 times, and each sequence would get a subset of the total vectors that need to be processed.  The commands in this section allow the user to override the default setup and explicitly specify the number of "simultaneous" processes to run, and what size chunk of vectors they should each grab.
'''

docsDescript['cmds'] = '''
The actual processing is done by a sequence of operations.  Each operation gets one or more vectors (for example, the ZF command operates on one vector at a time, but the TDCOMB command might get 2 vectors that need to be co-added).  Not explicitly specified, but implicitly included in each script is the fact that the beginning of the sequence is initialized by reading a group of vectors from a file, and ended by writing the processed vectors out to the dataset.
'''

def saveMarkDowDocs(rootName, docs):
    fileName = 'sandbox/docs/'+rootName+'/docs.md'
    with open(fileName,'w') as f1:
       f1.write(docs)

def genAllMarkDownDocs():
    docs = ''
    docs += '##File Commands##'
    docs += '\n'
    docs += docsDescript['file']
    docs += '\n'
    ops = ('FID','CREATE')
    docs += genRefDocsInMarkDown(ops)
    saveMarkDowDocs('01.filecmds', docs)

    docs = ''
    docs += '##Reference Commands##'
    docs += '\n'
    docs += docsDescript['ref']
    docs += '\n'
    ops = ('sw','acqsize', 'tdsize', 'sf','ref','label','printInfo')
    docs += genRefDocsInMarkDown(ops)
    saveMarkDowDocs('02.refcmds', docs)

    docs = ''
    docs += '##Processor Commands##'
    docs += '\n'
    docs += docsDescript['proc']
    docs += '\n'
    ops = ('procOpts','run')
    docs += genRefDocsInMarkDown(ops)
    saveMarkDowDocs('03.proccmds', docs)

    docs = ''
    docs += '##Processing Operations##'
    docs += '\n'
    docs += docsDescript['cmds']
    docs += '\n'
    docs += genCmdDocsInMarkDown()
    saveMarkDowDocs('04.operations', docs)

def genAllDocs():
    docs = ''
    docs += '<html\n'
    docs += '<body>\n'

    docs += '''
<head>
<style>
dl {
    margin-bottom:10px;
}

dl dt {
    font-weight:bold;
    margin-right:10px;
    padding:5px;
}

dl dd {
    padding:5px 0;
}

</style>
</head>
'''
    docs += '<h2>File Commands</h2>'
    docs += '''
<p>
The File commands are used at the beginning of each script to specify the raw NMR file (the FID) to open, and the NMRView dataset to create.
</p>
'''
    ops = ('FID','CREATE')
    docs += genRefDocs(ops)

    docs += '<h2>Referencing</h2>'
    docs += '''
<p>
Referencing commands are used at the beginning of each script to set the sweep width, spectrometer frequency, referencing and dataset axis labels.  The commands can take specific values that override anything in the FIDs parameter files, or they can be the names of parameters that are found in the vendors parameter files.  Bruker parameters are specified with the name, a comma, and a dimension number.  So, for example, "SFO1,1" would be the spectrometer frequency found in the "acqus" file, and "SFO1,2" would be the spectrometer frequency found in the "acqu2s" file.  The command "p" can be used to get the value of a parameter.  For example, p('sw2')/2.0  would get the value of the "sw2" parameter and divide it by 2.0
</p>
'''
    ops = ('sw','acqsize', 'tdsize', 'sf','ref','label','printInfo')
    docs += genRefDocs(ops)

    docs += '<h2>Processor</h2>'
    docs += '''
<p>
The actual sequence of processing operations is automatically parallelized and run on multiple processing cores.  For example, a MacBook Pro might have an Intel i7 processor with 4 cores.  Each of those cores is "hyperthreaded" so in total the CPU appears to the operating system (and NVFx's Java code) as if it can run 8 simultaneous operations.  The sequence of operations would therefore be replicated 8 times, and each sequence would get a subset of the total vectors that need to be processed.  The commands in this section allow the user to override the default setup and explicitly specify the number of "simultaneous" processes to run, and what size chunk of vectors they should each grab.
</p>
'''
    ops = ('procOpts','run')
    docs += genRefDocs(ops)
    docs += '<h2>Operations</h2>'
    docs += '''
<p>
The actual processing is done by a sequence of operations.  Each operation gets one or more vectors (for example, the ZF command operates on one vector at a time, but the TDCOMB command might get 2 vectors that need to be co-added).  Not explicitly specified, but implicitly included in each script is the fact that the beginning of the sequence is initialized by reading a group of vectors from a file, and ended by writing the processed vectors out to the dataset.
</p>
'''
    docs += genCmdDocs()
    docs += '</body></html>'
    return docs
