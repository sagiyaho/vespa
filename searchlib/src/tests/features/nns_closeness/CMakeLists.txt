# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_executable(searchlib_nns_closeness_test_app TEST
    SOURCES
    nns_closeness_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_nns_closeness_test_app COMMAND searchlib_nns_closeness_test_app)
