// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/countcompression.h>
#include <vespa/searchlib/bitcompression/pagedict4.h>

namespace search::diskindex {

class PageDict4RandRead : public index::DictionaryFileRandRead
{
    typedef bitcompression::PostingListCountFileDecodeContext DC;
    typedef bitcompression::PageDict4SSReader SSReader;

    typedef bitcompression::PageDict4SSLookupRes SSLookupRes;
    typedef bitcompression::PageDict4SPLookupRes SPLookupRes;
    typedef bitcompression::PageDict4PLookupRes PLookupRes;
    typedef bitcompression::PageDict4PageParams PageDict4PageParams;

    typedef index::PostingListCounts PostingListCounts;
    typedef index::PostingListOffsetAndCounts PostingListOffsetAndCounts;

    std::unique_ptr<SSReader> _ssReader;

    DC _ssd;
    ComprFileReadContext _ssReadContext;
    std::unique_ptr<FastOS_FileInterface> _ssfile;
    std::unique_ptr<FastOS_FileInterface> _spfile;
    std::unique_ptr<FastOS_FileInterface> _pfile;

    uint64_t _ssFileBitSize;
    uint64_t _spFileBitSize;
    uint64_t _pFileBitSize;
    uint32_t _ssHeaderLen;
    uint32_t _spHeaderLen;
    uint32_t _pHeaderLen;

    void readSSHeader();
    void readSPHeader();
    void readPHeader();
public:
    PageDict4RandRead();
    ~PageDict4RandRead();

    bool lookup(vespalib::stringref word, uint64_t &wordNum,
                PostingListOffsetAndCounts &offsetAndCounts) override;

    bool open(const vespalib::string &name, const TuneFileRandRead &tuneFileRead) override;

    bool close() override;
    uint64_t getNumWordIds() const override;
};

}
