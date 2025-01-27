# Cloverage Changelog

<!-- Pull requests are required to update this changelog.  Changelog entries should
mention and link to any issues or tickets involved in the change, and should
provide a short summary description of the particular changes of the patch.

Include the issue number (#xxx) which will link back to the originating issue
in github. Commentary on the change should appear as a nested, unordered list. -->

## 1.2.5

- Option to set thresholds separately for line and form coverage
  - Fixes [#344](https://github.com/cloverage/cloverage/issues/344)
- Fix `[:runner-opt :report]` in the eftest runner
  - Fixes [#328](https://github.com/cloverage/cloverage/issues/328)
- Add support for Clojure 1.12
  - Fixes [#345](https://github.com/cloverage/cloverage/issues/345)
  - Fixes [#347](https://github.com/cloverage/cloverage/issues/347)

## 1.2.4

- Make validation errors in the project settings fail Cloverage runs
  - Fixes [#331](https://github.com/cloverage/cloverage/issues/331)
- Upgrade various dependencies
  - Fixes [218](https://github.com/cloverage/cloverage/issues/218)
  - Fixes [319](https://github.com/cloverage/cloverage/issues/319)
  - Fixes [334](https://github.com/cloverage/cloverage/issues/334)

Also some more development niceties, like improvements to CI (including a
proper CircleCI test matrix).

## 1.2.3

- Fix loss of isolated namespaces during reordering (#312)

Also a bunch of internal development niceties, like improvements to CI and
resource management. Shouldn't be externally visible.

## 1.2.2

- Use known data readers while reading forms (#313)
- Support for eftest as a test runner with `--runner :eftest` (#314)

## 1.2.1

- Bugfixes
  - Fix broken instrumentation when a symbol naming a special form such as
    `var` or `new` was defined in the current namespace or used in a local
    `let` binding (#247, #280, #301)
  - Fix the order in which namespaces get instrumented, which could case false
    negatives in forms that are only evaluated the first time the namespace is
    loaded (#294, #302, #303)
  - Fix instrumention of inlined function calls like `int` (#277, #304)
  - Performance improvements (#304)
  - Instrument the class-or-instance part of Java interop forms (#306, #307)
  - Propagate tag metadata when instrumenting function call forms (#308, #310)
  - Fix instrumenting static interop calls (#309, #311)

## 1.2.0 (BUGFIX RELEASE)

1.1.3, specifically PR #292, introduced a regression that caused some
expressions to be evaluated twice, breaking tests. #299 introduces an
alternative implementation that maintains the metadata capturing behavior but
appears to resolve the regression.

This was the only change: everyone on 1.1.3 should upgrade to 1.2.0.

## 1.1.3

Thanks to everyone who contributed this release! A _lot_ of people ended up
scratching their own itches and making Cloverage better for everyone. Particular
thanks to @camsaul for a _ton_ of improvements that were a pleasure to
incorporate and review.

- Improvements
  - Preserve metadata while wrapping collections (#291, #292)
  - Preserve metadata while instrumenting forms (#282, #279)
  - Better failure modes when forms can't be instrumented (#278, #257, refs #277)
  - Automatically activate test profile when running Cloverage (#290, #289)
  - Make colorization optional with `--(no-)colorize` (#267)
  - Allow excluding `doseq` (#264, #263)
  - CI improvements (#287, #221, #297)
  - Documentation improvements (#285, #283)
  - Internal code quality and test improvements (#281, #276, #275, #272)
  - Updated dependencies (#259)
- Bugfixes
  - Don't attempt to :aot clojure.tools.reader (#288, #268)
  - Fix instrumentation for generated defrecord forms (#273, #271, #257)
  - Make the tests work on non-en_US platforms (#266)

## 1.1.2

- Improvements
  - Support for deftype forms (#254)
  - Custom data readers are now installed before instrumentation (#255, #197)
  - Better error message when no namespaces selected for instrumentation (#245)
  - Travis CI tests now run on Ubuntu Trusty (#256)
  - License cleanup (no changes, just housekeeping) (#249, #251)
  - Documentation for using cloverage with deps.edn/clj ecosystem (#248)
- Bugfixes
  - :exclude-calls didn't work because of an incorrect type conversion (#253)

## 1.1.1

- Improvements
  - Support for excluding call sites by fn/macro name (#242)
- Bugfixes
  - Fix lcov output format (bug: #225, PR: #244)

## 1.1.0
- Improvements
  - Add the --custom-report option to build your own formatter
  - Add support for test selectors (see #54 and #230)
  - Significant performance improvements (#236)
- Bugfixes
  - Fix the handling of --ns-regex and --test-ns-regex (#183)
  - Read options from the Leiningen project file (#155)
  - Fix instrumentation of reify forms (#185)

## 1.0.10
- Improvements
  - Split out reporting into separate namespaces (#165)
  - Stop using Cheshire for JSON output (fewer transitive dependencies) (#165)
  - No more reflection warnings (again)! (#180)
  - Automatidcally try to create directory tree necessary for reports (#192)
  - External runner support: after midje and clojure.test, you can now write your own (#193)
- Bugfixes
  - Fix performance regression: Only call `gather-stats` once (#166)
  - Abort if cyclic dependency in namespaces detected (#122)
  - Attempt creation of output dir ahead of running with --junit flag (#167)
  - Only auto-add cloverage to dependencies when you haven't set it manually already (#195)

## 1.0.9
- Improvements
  - No more reflection warnings! (#158)
  - Colorized summary report & ability to fail build on coverage &lt; threshold (#99)
- Bugfixes
  - Passthrough _-h_ / _--help_ flag to properly show help options (#156)

## 1.0.8
- Features
  - Add [Code of Conduct](https://github.com/cloverage/cloverage/blob/master/CODE_OF_CONDUCT.md) (#128)
  - Add junit support with the `--junit` flag (#127)
- Improvements
  - Coverage within `for` comprehensions is always partial (#23)
  - Move Changelog to separate file (#144)

## 1.0.7
- Features
  - Add codecov.io support with the `--codecov` flag (#78)
  - Add lcov (e.g. coverlay) support with the `--lcov` flag (#114)
  - Support for midje as a test runner with `--runner :midje` (#64)
  - Support for cljc files (#93/#94)
- Improvements
  - Coverage fn (internal hot loop) optimization (#90)
  - Dependency upgrades, including running tests on Oracle JDK 8 (#105)
- Bugfixes
  - Fix Unicode (UTF-8) support for HTML output (#100)
  - Fix handling of multibyte characters (#108)
  - Fix HTML entity encoding bug (#55)
  - Coveralls report: fix source digest, line hit numbers (#96)

## 1.0.6
- Features
  - Option to exclude namespaces (#57/#73)
  - Improved records fixes for Compojure (#66/#69)
  - Option to specify a path to src/test namespaces (#70)
  - Automatic push out of snapshot releases (#65)
  - Handle records correctly (#59)
  - Text summary of results (#50)
- Bugfixes
  - Correct test namespaces regex usage (#67)
  - Cope with zero-namespace situations correctly (#62)

## 1.0.5
- Bugfixes:
 - Work around AOT-ed inline functions not being wrappable (http://dev.clojure.org/jira/browse/CLJ-1330)

## 1.0.4
- Features:
 - Minimal EMMA XML output format support.
 - [Coveralls](https://coveralls.io) output format.
 - Cloverage now exits with non-zero exit code when your tests fail
 - Total % coverage summary in index.html
- Bugfixes:
 - Better instrumentation logic is no longer confused by macro/symbol shadowing
 - Support for (:require [(namespace.prefix (suffix :as rename))]) ns forms
 - Cloverage jars no longer include all dependencies

## 1.0.3
 - fix empty list crash
 - add letfn support
 - print html report URL after testing
