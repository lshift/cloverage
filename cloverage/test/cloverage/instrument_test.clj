(ns cloverage.instrument-test
  (:require [clojure.string :as str]
            [clojure.test :as t]
            [clojure.tools.logging :as log]
            [cloverage.instrument :as inst]
            [riddley.walk :as rw]))

(def simple-forms
  "Simple forms that do not require macroexpansion and have no side effects."
  [1
   "A STRING"
   ''("a" "simple" "list")
   [1 2 'vector 3 4]
   {:simple :map :here 1}
   #{:sets :should :work}
   '(do :expression)])

(t/deftest wrap-preserves-value
  (doseq [simple-expr simple-forms]
    (t/is (= simple-expr (rw/macroexpand-all (inst/wrap #'inst/no-instr 0 simple-expr))))
    (t/is (= (eval simple-expr) (eval (inst/wrap #'inst/nop 0 simple-expr))))))

(t/deftest correctly-resolves-macro-symbols
  ;; simply ensure that instrumentation succeeds without errors
  (t/is (inst/instrument #'inst/no-instr 'cloverage.sample.read-eval-sample)))

(defprotocol Protocol
  (method [this]))

(defrecord Record [foo]
  Protocol
  (method [_] foo))

(t/deftest test-form-type
  (doseq [[message form->expected]
          {"Atomic forms"
           {[1]     :atomic
            ["foo"] :atomic
            ['bar]  :atomic}

           "Collections"
           {[[1 2 3 4]]   :coll
            [{1 2 3 4}]   :coll
            [#{1 2 3 4}]  :coll
            [(Record. 1)] :coll}

           "do & loop"
           {['(do 1 2 3)]               :do
            ;; fake a local binding
            '[(loop 1 2 3) {loop hoop}] :list}

           "Inlined function calls"
           {['(int 1)]            :inlined-fn-call
            [`(int 1)]            :inlined-fn-call
            [`(int 1) '{int int}] :inlined-fn-call}

           "Local vars overshadow inlined fn calls; make sure this is detected correctly"
           {'[(int) {int int}] :list}

           "If some arities are inlined and others are not, only return `:inlined-fn-call` when inlined"
           {['(+ 1)]   :list
            ['(+ 1 2)] :inlined-fn-call}

           "Lists starting with other lists"
           {['((or + 1) 1 2)] :list}

           "List-like classes e.g. clojure.lang.Cons for which `list?` is false"
           {[(cons 1 '(2 3))] :list}}

          [[form env] expected] form->expected]
    (t/testing message
      (t/testing (str "\n" (pr-str (list 'form-type form env)))
        (t/is (= expected
                 (inst/form-type form env)))))))

(t/deftest do-wrap-for-record-returns-record
  (t/is (= 1 (method (eval (inst/wrap #'inst/nop 0 (Record. 1)))))))

(t/deftest do-wrap-for-record-func-key-returns-func
  (t/is (= 1 ((method (eval (inst/wrap #'inst/nop 0 (Record. (fn [] 1)))))))))

(t/deftest preserves-fn-conditions
  (let [pre-fn (eval (inst/wrap #'inst/nop 0
                                '(fn [n] {:pre [(> n 0) (even? n)]} n)))]
    (t/is (thrown? AssertionError (pre-fn -1)))
    (t/is (thrown? AssertionError (pre-fn 1)))
    (t/is (= 2 (pre-fn 2))))
  (let [post-fn (eval (inst/wrap #'inst/nop 0
                                 '(fn [n] {:post [(> % 3) (even? %)]} n)))]
    (t/is (thrown? AssertionError (post-fn 1)))
    (t/is (thrown? AssertionError (post-fn 5)))
    (t/is (= 4 (post-fn 4))))
  ;; XXX: side effect, but need to test defn since we special case it
  (let [both-defn (eval (inst/wrap #'inst/nop 0
                                   '(defn both-defn [n]
                                      {:pre [(> n -1)] :post [(> n 0)]}
                                      n)))]
    (t/is (thrown? AssertionError (both-defn 0)))
    (t/is (thrown? AssertionError (both-defn -1)))
    (t/is (= 1 (both-defn 1)))))

(t/deftest test-exclude-calls
  (let [form    '(doseq [_ 100])
        wrapped (inst/do-wrap #'inst/nop 42 form {})]
    (t/is (not= form wrapped))
    (binding [inst/*exclude-calls* #{'clojure.core/doseq}]
      (let [wrapped (inst/do-wrap #'inst/nop 42 form {})]
        (t/is (= form wrapped))))))

(t/deftest test-wrap-defrecord-methods
  (let [form    (list 'defrecord 'MyRecord []
                      'Protocol
                      (list 'method []
                            (with-meta '(do-something) {:line 1337})))
        wrapped (list 'defrecord 'MyRecord []
                      'Protocol
                      (list 'method []
                            (inst/wrap #'inst/no-instr 1337 '(do-something))))]
    (t/is (not= form wrapped))
    (t/is (= wrapped
             (inst/do-wrap #'inst/no-instr 0 form nil))
          "Lines inside defrecord methods should get wrapped.")))

(defmacro my-defrecord [& args]
  `(do (defrecord ~@args)))

(t/deftest test-wrap-generated-defrecord-methods
  (t/testing "defrecord forms generated by macros such as Potemkin defrecord+ should get wrapped correctly"
    (let [form    (list 'do (list 'cloverage.instrument-test/my-defrecord 'MyRecord []
                                  'Protocol
                                  (with-meta (list 'method []
                                                   (with-meta '(do-something) {:line 1337}))
                                    {:line 1})))]
      (t/is (= `(do (do (defrecord ~'MyRecord []
                          ~'Protocol
                          ~'(method [] (cloverage.instrument/wrapm cloverage.instrument/no-instr 1337 (do-something))))))
               (inst/do-wrap #'inst/no-instr 0 form nil))))))

(t/deftest test-wrap-deftype-methods
  ;; (deftype ...) expands to (let [] (deftype* ...))
  ;; ignore the let form & binding because we're only interested in how `deftype*` gets instrumented
  (let [form (nth
              (macroexpand-1
               (list 'deftype 'MyType []
                     'Protocol
                     (list 'method []
                           (with-meta '(do-something) {:line 1337}))))
              2)
        wrapped (nth
                 (macroexpand-1
                  (list 'deftype 'MyType []
                        'Protocol
                        (list 'method []
                              (inst/wrap #'inst/no-instr 1337 '(do-something)))))
                 2)]
    (t/is (= (first form) 'deftype*)) ; make sure we're actually looking at the right thing
    (t/is (not= form wrapped))
    (t/is (= wrapped
             (inst/do-wrap #'inst/no-instr 0 form nil))
          "Lines inside deftype methods should get wrapped.")))

(t/deftest test-deftype-defrecord-line-metadata
  ;; * If an individual line in a defrecord or deftype method body has ^:line metadata, we should use that (3 in the
  ;;   test below)
  ;;
  ;; * Failing that, if the entire (method [args*] ...) form has line number metadata, we should use that (2 in the
  ;;   test below)
  ;;
  ;; * Finally, we should fall back to using the line number passed in to `wrap-deftype-defrecord-methods`
  ;; * (presumably the line of the entire `defrecord`/`deftype` form) (1 in the test below)
  (let [form    (list
                 'defrecord 'MyRecord []
                 'Protocol
                 (-> (list 'method-with-meta []
                           (with-meta '(line-with-meta) {:line 3})
                           (with-meta '(line-without-meta) nil))
                     (with-meta {:line 2}))
                 (-> (list 'method-without-meta [] (with-meta '(line-without-meta) nil))
                     (with-meta nil)))
        wrapped (list 'defrecord 'MyRecord []
                      'Protocol
                      (list 'method-with-meta []
                            (inst/wrap #'inst/no-instr 3 '(line-with-meta))
                            (inst/wrap #'inst/no-instr 2 '(line-without-meta)))
                      (list 'method-without-meta []
                            (inst/wrap #'inst/no-instr 1 '(line-without-meta))))]
    (t/is (= wrapped
             (inst/do-wrap #'inst/no-instr 1 form nil))
          "Wrapped defrecord/deftype methods should use most-specific line number metadata available.")))

(t/deftest test-instrumenting-fn-forms-preserves-metadata
  (let [form         '(.submit clojure.lang.Agent/pooledExecutor ^java.lang.Runnable (fn []))
        instrumented (rw/macroexpand-all (inst/instrument-form #'inst/nop nil ^{:line 1} form))]
    (t/is (= '(do (. clojure.lang.Agent/pooledExecutor submit (do (fn* ([])))))
             instrumented))
    (let [[_ fn-form :as do-form] (-> instrumented last last)]
      (t/testing "metadata on the fn form should be propagated to the wrapper instrumentation form"
        (t/is (= '(do (fn* ([])))
                 do-form))
        (t/is (= 'java.lang.Runnable
                 (:tag (meta do-form)))))
      (t/testing "metadata on the fn form should be preserved"
        (t/is (= '(fn* ([]))
                 fn-form))
        (t/is (= 'java.lang.Runnable
                 (:tag (meta fn-form))))))))

(t/deftest test-instrumenting-fn-call-forms-propogates-metadata
  (t/testing "Tag info for a function call form should be included in the instrumented form (#308)"
    ;; e.g. (str "Oops") -> ^String (do ((do str) (do "Oops")))
    (let [form         '(new java.lang.IllegalArgumentException (str "No matching clause"))
          instrumented (rw/macroexpand-all (inst/wrap #'inst/nop nil form))]
      (t/is (= '(do (new java.lang.IllegalArgumentException (do ((do str) (do "No matching clause")))))
               instrumented))
      (let [fn-call-form (-> instrumented last last)]
        (t/is (= '(do ((do str) (do "No matching clause")))
                 fn-call-form))
        (t/is (= java.lang.String
                 (:tag (meta fn-call-form)))))))

  (t/testing "Should also work if tag was specified on the entire form"
    (let [form         '(let [my-str (fn [& args]
                                       (apply str args))]
                          (new java.lang.IllegalArgumentException ^String (my-str "No matching clause")))
          instrumented (rw/macroexpand-all (inst/wrap #'inst/nop nil form))]
      (t/is (= '(do (let* [my-str (do (fn* ([& args]
                                            (do ((do apply) (do str) (do args))))))]
                          (do (new java.lang.IllegalArgumentException
                                   (do ((do my-str) (do "No matching clause")))))))
               instrumented))
      (let [fn-call-form (-> instrumented last last last last)]
        (t/is (= '(do ((do my-str) (do "No matching clause")))
                 fn-call-form))
        (t/is (= 'String
                 (:tag (meta fn-call-form))))))))

(t/deftest test-coll-preserves-metadata
  (let [form         ^:preserved? [:foo :bar]
        instrumented (macroexpand-1 (inst/instrument-form #'inst/no-instr
                                                          nil
                                                          form))]
    (t/is (:preserved? (meta instrumented)))))

(def test-coll-is-evaluated-once-count
  "This needs to be reachable from global scope."
  (atom 0))

(t/deftest test-coll-is-evaluated-once
  (reset! test-coll-is-evaluated-once-count 0)
  (inst/instrument-form #'inst/no-instr nil `{:foo (swap! test-coll-is-evaluated-once-count inc)})
  (t/is (= 1 @test-coll-is-evaluated-once-count)))

(t/deftest fail-gracefully-when-instrumenting
  (t/testing "If instrumenting a form fails we should log an Exception and continue instead of failing entirely."
    (let [form                   '(this-function-does-not-exist 100)
          log-messages           (atom [])
          evalled-original-form? (atom false)
          orig-eval-form         inst/eval-form]
      (with-redefs [log/log*       (fn [_ & message]
                                     (swap! log-messages conj (vec message)))
                    ;; `eval-form` should be called twice -- once with the instrumented form, which will fail, and a
                    ;; second time with an uninstrumented form.
                    ;;
                    ;; Both times would normally fail because `this-function-does-not-exist` doesn't exist, but for
                    ;; the sake of theses tests we want to pretend evaling the original form the second time around
                    ;; works normally. So for the second call just record the fact that it happened any avoid actually
                    ;; evalling it
                    inst/eval-form (fn [filename form line-hint instrumented-form]
                                     (if (identical? form instrumented-form)
                                       (reset! evalled-original-form? true)
                                       (orig-eval-form filename form line-hint instrumented-form)))]
        (t/testing "instrument-form should return uninstrumented form as-is"
          (t/is (= form
                   (inst/instrument-form #'inst/no-instr nil form))))
        (t/testing "Exception should be logged"
          (t/is (= 1
                   (count @log-messages)))
          (let [[[message-type _ message]] @log-messages]
            (t/is (= :error
                     message-type))
            (doseq [s ["Error evaluating instrumented form"
                       "Unable to resolve symbol: this-function-does-not-exist"]]
              (t/is (str/includes? message s)
                    (str "Error message should include %s" (pr-str s))))))
        (t/testing "We should have attempted to evauluate the *original* form"
          (t/is (= true
                   @evalled-original-form?)))))))

(t/deftest instrument-java-interop-forms-test
  (t/testing "Java interop forms should get instrumented correctly (#304)"
    ;; these two syntaxes are equivalent
    (t/testing "(. class-or-instance method & args) syntax"
      (t/is (= '(do (. clojure.lang.RT count (do [(do 3) (do 4)])))
               (rw/macroexpand-all (inst/instrument-form #'inst/nop nil '(. clojure.lang.RT count [3 4]))))))
    (t/testing "(. class-or-instance (method & args)) syntax"
      (t/is (= '(do (. clojure.lang.RT (count (do [(do 3) (do 4)]))))
               (rw/macroexpand-all (inst/instrument-form #'inst/nop nil '(. clojure.lang.RT (count [3 4])))))))
    (t/testing "class-or-instance part of Java interop form should get instrumented if not a class or symbol (#306)"
      (t/is (= '(do (. (do (let* [x (do 1)]
                                 (do ((do str)
                                      (do "X is")
                                      (do x)))
                                 (do ((do Thread/currentThread)))))
                       getName))
               (rw/macroexpand-all (inst/instrument-form #'inst/nop nil '(.getName (let [x 1]
                                                                                     (str "X is" x)
                                                                                     (Thread/currentThread)))))))
      (t/testing "Class should not get instrumented (#309)"
        (let [form (list '. clojure.lang.RT 'nth [:a :b :c] 0 nil)]
          (t/is (= :a
                   (eval form)))
          (t/is (= (list 'do (list '. clojure.lang.RT 'nth '(do [(do :a) (do :b) (do :c)]) '(do 0) '(do nil)))
                   (rw/macroexpand-all (inst/instrument-form #'inst/nop nil form)))))))))

(t/deftest instrument-inlined-primitives-test
  (t/testing "Inline primitive cast functions like int() should be instrumented correctly (#277)"
    (t/is (= '(. clojure.lang.RT (intCast 2))
             (rw/macroexpand-all (inst/instrument-form #'inst/no-instr nil '(int 2)))
             (rw/macroexpand-all (inst/instrument-form #'inst/no-instr nil `(int 2)))))
    (t/testing "make sure example in #277 actually gets instrumented and doesn't fall back to returning the original form"
      (let [form '(deftype MyType [^:unsynchronized-mutable ^int i]
                    clojure.lang.IHashEq
                    (hasheq [_]
                      (set! i (int 2))))]
        (t/is (not= form
                    (inst/instrument-form #'inst/no-instr nil form)))))
    (t/testing "Non-inlineable uses of functions like `int` should get instrumented normally"
      (t/is (= `(map int [1])
               (rw/macroexpand-all (inst/instrument-form #'inst/no-instr nil `(map int [1]))))))
    (t/testing "For functions that are sometimes inlined (e.g `+`) make sure we instrument it appropriately in both cases"
      (t/testing "Not inlined"
        (t/is (= '(do ((do +) (do 1)))
                 (rw/macroexpand-all (inst/instrument-form #'inst/nop nil '(+ 1))))))
      (t/is (= '(do (. clojure.lang.Numbers (add (do 1) (do 2))))
               (rw/macroexpand-all (inst/instrument-form #'inst/nop nil '(+ 1 2)))))
      (t/is (= '(do (. clojure.lang.Numbers (add (do ((do identity) (do 1))) (do 2))))
               (rw/macroexpand-all (inst/instrument-form #'inst/nop nil '(+ (identity 1) 2))))))
    (t/testing "Inlined calls inside inlined calls should get instrumented correctly"
      (t/is (= '(do
                  (. clojure.lang.Numbers
                     (add (do 1)
                          (do (. clojure.lang.RT (clojure.core/count (do [(do 2) (do (if (do *print-meta*)
                                                                                       (do 3)
                                                                                       (do 4)))])))))))
               (rw/macroexpand-all (inst/instrument-form #'inst/nop nil '(+ 1 (count [2 (if *print-meta* 3 4)]))))))
      (t/is (= `(do (.
                     clojure.lang.Util
                     clojure.core/equiv
                     (do (.
                          clojure.lang.RT
                          (clojure.core/count (do [(do 1) (do 2)]))))
                     (do (.
                          clojure.lang.RT
                          (clojure.core/count (do [(do 3) (do 4)]))))))
               (rw/macroexpand-all (inst/instrument-form #'inst/nop nil '(= (count [1 2]) (count [3 4])))))))))

(defmacro clj-version-or-higher
  [target-version & body]
  `(if (and (>= (compare (clojure-version) ~target-version) 0))
     ~@body
     (t/is (true? true))))

(t/deftest instrument-clj-1-12-features
  (clj-version-or-higher
   "1.12.0"
   (t/testing "Instrumentation of new Clojure 1.12 features"
     (t/testing "Qualified methods - Class/method, Class/.method, and Class/new"
       (t/is (= '(do
                   (do
                     (do ((do Long/new) (do 1)))
                     (do System/out)
                     (do (let* [f (do Long/.byteValue)]
                           (do ((do f) (do 1)))))
                     (do (let* [f (do Long/valueOf)]
                           (do ((do f) (do 1)))))))
                (rw/macroexpand-all (inst/instrument-form #'inst/nop
                                                          nil
                                                          '(do
                                                             (Long/new 1)
                                                             System/out
                                                             (let [f Long/.byteValue]
                                                               (f 1))
                                                             (let [f Long/valueOf]
                                                               (f 1))))))))
     (t/testing "Functional interfaces"
       (t/is (= '(do
                   (let* [p (do even?)]
                     (do (. p test (do 42)))))
                (rw/macroexpand-all (inst/instrument-form #'inst/nop
                                                          nil
                                                          '(let [^java.util.function.Predicate p even?]
                                                             (.test p 42)))))))
     (t/testing "Array class syntax"
       (t/is (= '(do
                   (do
                     (do (new ProcessBuilder (do ((do into-array) (do String) (do [(do "a")])))))
                     (do ((do java.util.Arrays/binarySearch)
                          (do (. clojure.lang.Numbers clojure.core/int_array (do [(do 1) (do 2) (do 3)])))
                          (do (. clojure.lang.RT (intCast (do 2))))))))
                (rw/macroexpand-all (inst/instrument-form #'inst/nop
                                                          nil
                                                          '(do
                                                             (ProcessBuilder. ^String/1 (into-array String ["a"]))
                                                             (java.util.Arrays/binarySearch ^int/1 (int-array [1 2 3])
                                                                                            (int 2)))))))))))
