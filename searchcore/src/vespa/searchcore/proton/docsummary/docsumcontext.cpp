// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "docsumcontext.h"
#include <vespa/searchcore/proton/matching/matcher.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/searchlib/queryeval/begin_and_end_id.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/common/location.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.docsummary.docsumcontext");

using document::PositionDataType;
using search::common::Location;
using vespalib::string;
using vespalib::slime::SymbolTable;
using vespalib::slime::NIX;
using vespalib::Memory;
using vespalib::slime::Cursor;
using vespalib::slime::Symbol;
using vespalib::slime::Inserter;
using vespalib::slime::ObjectSymbolInserter;
using vespalib::Slime;
using vespalib::make_string;
using namespace search;
using namespace search::attribute;
using namespace search::engine;
using namespace search::docsummary;

namespace proton {

using namespace matching;

namespace {

Memory DOCSUMS("docsums");
Memory DOCSUM("docsum");
Memory ERRORS("errors");
Memory TYPE("type");
Memory MESSAGE("message");
Memory DETAILS("details");
Memory TIMEOUT("timeout");

}

void
DocsumContext::initState()
{
    const DocsumRequest & req = _request;
    _docsumState._args.initFromDocsumRequest(req);
    _docsumState._docsumcnt = req.hits.size();

    _docsumState._docsumbuf = (_docsumState._docsumcnt > 0)
                              ? (uint32_t*)malloc(sizeof(uint32_t) * _docsumState._docsumcnt)
                              : nullptr;

    for (uint32_t i = 0; i < _docsumState._docsumcnt; i++) {
        _docsumState._docsumbuf[i] = req.hits[i].docid;
    }
}

namespace {

vespalib::Slime::Params
makeSlimeParams(size_t chunkSize) {
    Slime::Params params;
    params.setChunkSize(chunkSize);
    return params;
}

}

vespalib::Slime::UP
DocsumContext::createSlimeReply()
{
    _docsumWriter.InitState(_attrMgr, &_docsumState);
    const size_t estimatedChunkSize(std::min(0x200000ul, _docsumState._docsumcnt*0x400ul));
    vespalib::Slime::UP response(std::make_unique<vespalib::Slime>(makeSlimeParams(estimatedChunkSize)));
    Cursor & root = response->setObject();
    Cursor & array = root.setArray(DOCSUMS);
    const Symbol docsumSym = response->insert(DOCSUM);
    IDocsumWriter::ResolveClassInfo rci = _docsumWriter.resolveClassInfo(_docsumState._args.getResultClassName(),
                                                                         _docsumStore.getSummaryClassId());
    _docsumState._omit_summary_features = rci.outputClass->omit_summary_features();
    uint32_t i(0);
    for (i = 0; (i < _docsumState._docsumcnt) && !_request.expired(); ++i) {
        uint32_t docId = _docsumState._docsumbuf[i];
        Cursor & docSumC = array.addObject();
        ObjectSymbolInserter inserter(docSumC, docsumSym);
        if ((docId != search::endDocId) && !rci.mustSkip) {
            _docsumWriter.insertDocsum(rci, docId, &_docsumState, &_docsumStore, *response, inserter);
        }
    }
    if (i != _docsumState._docsumcnt) {
        const uint32_t numTimedOut = _docsumState._docsumcnt - i;
        Cursor & errors = root.setArray(ERRORS);
        Cursor & timeout = errors.addObject();
        timeout.setString(TYPE, TIMEOUT);
        timeout.setString(MESSAGE, make_string("Timed out %d summaries with %" PRId64 "us left.",
                                               numTimedOut, vespalib::count_us(_request.getTimeLeft())));
    }
    return response;
}

DocsumContext::DocsumContext(const DocsumRequest & request, IDocsumWriter & docsumWriter,
                             IDocsumStore & docsumStore, std::shared_ptr<Matcher> matcher,
                             ISearchContext & searchCtx, IAttributeContext & attrCtx,
                             IAttributeManager & attrMgr, SessionManager & sessionMgr) :
    _request(request),
    _docsumWriter(docsumWriter),
    _docsumStore(docsumStore),
    _matcher(std::move(matcher)),
    _searchCtx(searchCtx),
    _attrCtx(attrCtx),
    _attrMgr(attrMgr),
    _docsumState(*this),
    _sessionMgr(sessionMgr)
{
    initState();
}

DocsumReply::UP
DocsumContext::getDocsums()
{
    return std::make_unique<DocsumReply>(createSlimeReply());
}

void
DocsumContext::FillSummaryFeatures(search::docsummary::GetDocsumsState * state, search::docsummary::IDocsumEnvironment *)
{
    assert(&_docsumState == state);
    if (_matcher->canProduceSummaryFeatures()) {
        state->_summaryFeatures = _matcher->getSummaryFeatures(_request, _searchCtx, _attrCtx, _sessionMgr);
    }
    state->_summaryFeaturesCached = false;
}

void
DocsumContext::FillRankFeatures(search::docsummary::GetDocsumsState * state, search::docsummary::IDocsumEnvironment *)
{
    assert(&_docsumState == state);
    // check if we are allowed to run
    if ( ! state->_args.dumpFeatures()) {
        return;
    }
    state->_rankFeatures = _matcher->getRankFeatures(_request, _searchCtx, _attrCtx, _sessionMgr);
}

std::unique_ptr<MatchingElements>
DocsumContext::fill_matching_elements(const MatchingElementsFields &fields)
{
    if (_matcher) {
        return _matcher->get_matching_elements(_request, _searchCtx, _attrCtx, _sessionMgr, fields);
    }
    return std::make_unique<MatchingElements>();
}

} // namespace proton
