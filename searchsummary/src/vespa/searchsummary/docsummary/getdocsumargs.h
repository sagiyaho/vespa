// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/searchlib/engine/propertiesmap.h>

namespace search::docsummary {

class GetDocsumArgs
{
private:
    vespalib::string   _resultClassName;
    bool               _dumpFeatures;
    bool               _locations_possible;
    std::vector<char>  _stackDump;
    vespalib::string   _location;
    vespalib::duration _timeout;
    fef::Properties    _highlightTerms;
public:
    GetDocsumArgs();
    ~GetDocsumArgs();

    void initFromDocsumRequest(const search::engine::DocsumRequest &req);

    void setResultClassName(vespalib::stringref name) { _resultClassName = name; }
    void SetStackDump(uint32_t stackDumpLen, const char *stackDump);
    void locations_possible(bool value) { _locations_possible = value; }
    bool locations_possible() const { return _locations_possible; }
    const vespalib::string &getLocation() const { return _location; }
    void setLocation(const vespalib::string & location) { _location = location; }
    void setTimeout(vespalib::duration timeout) { _timeout = timeout; }
    vespalib::duration getTimeout() const { return _timeout; }

    const vespalib::string & getResultClassName()      const { return _resultClassName; }
    const vespalib::stringref getStackDump()           const {
        return vespalib::stringref(&_stackDump[0], _stackDump.size());
    }

    void dumpFeatures(bool v) { _dumpFeatures = v; }
    bool dumpFeatures() const { return _dumpFeatures; }

    const fef::Properties &highlightTerms() const {
        return _highlightTerms;
    }
};

}
