# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_sortbenchmark_app
    SOURCES
    sortbenchmark.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_sortbenchmark_app COMMAND searchlib_sortbenchmark_app BENCHMARK)
vespa_add_executable(searchlib_sort_test_app
    SOURCES
    sort_test.cpp
    DEPENDS
    searchlib
    searchlib_searchlib_uca
)
#vespa_add_test(NAME searchlib_sort_test_app COMMAND searchlib_sort_test_app)
vespa_add_executable(searchlib_uca_stress_app
    SOURCES
    uca.cpp
    DEPENDS
    searchlib
    searchlib_searchlib_uca
)
vespa_add_test(NAME searchlib_uca_stress_app COMMAND searchlib_uca_stress_app BENCHMARK)
