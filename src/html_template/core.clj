(ns html-template.core
  (:use name.choi.joshua.fnparse clojure.set clojure.test clojure.pprint))

;; <table border=1>
;;   <!-- TMPL_LOOP rows -->
;;     <tr>
;;       <!-- TMPL_LOOP cols -->
;;         <!-- TMPL_IF colorful-style -->
;;           <td align="right" bgcolor="pink"><!-- TMPL_VAR content --></td>
;;         <!-- TMPL_ELSE -->
;;           <td align="right" ><!-- TMPL_VAR content --></td>
;;         <!-- /TMPL_IF -->
;;       <!-- /TMPL_LOOP -->
;;     </tr>
;;   <!-- /TMPL_LOOP -->
;; </table>

;; (let* ((rows (loop for i below 49 by 7
;;                    collect (list :cols
;;                                  (loop for j from i below (+ i 7)
;;                                        for string = (format nil "~R" j)
;;                                        collect (list :content string
;;                                                      :colorful-style (oddp j))))))
;;        (values (list :rows rows)))
;;   (fill-and-print-template #p"/tmp/foo.tmpl" values))

(defn range-by-old [start end step]
  (reverse (reduce (fn [x y] (if (or (= 0 y) (= 0 (mod y step))) (conj x y) x)) nil (range start end))))

(defn range-by [start end step]
  (remove #(and (> % 0) (not= 0 (mod % step))) (range start end)))

(defn build-row [start end]
  `{ :cols ~(vec (map (fn [n] `{ :content ~(cl-format nil "~R" n) :colorful-style ~(odd? n)}) (range start end)))})

(defn build-rows [start end]
  `{ :rows ~(vec (map (fn [row-start] (build-row row-start (+ row-start 7))) (range-by start end 7)))})

(defn template-code [values]
  (println "<table border=1>")
  (doseq [rows (get values :rows)]
    (println "<tr>")
    (doseq [cols (get rows :cols)]
      (if (get cols :colorful-style)
        (do 
          (print "          <td align=\"right\" bgcolor=\"pink\">")
          (print (get cols :content))
          (println "</td>"))
        (do
          (print "          <td align=\"right\" >")
          (print (get cols :content))
          (println "</td>"))))
    (println "</tr>"))
  (println "</table>"))
  

;; push_context
;; emit
;; loop
;; emit
;; loop
;; emit_if_else
;; end_loop
;; emit
;; end_loop
;; pop_context

;(def foo { :rows [[ :cols [ { :content "one" :colorful-style false } { :content "two" :colorful-style true } ]]]})

(defn nb-char-lit [ch]
  (lit ch))

;(defn- b-char [args]
 ; (args))

(def space (nb-char-lit \space))

(def tab (nb-char-lit \tab))

(def newline-lit (lit \newline))

(def return-lit (lit \return))

; (def line-break (b-char (rep+ (alt newline-lit return-lit))))
(def line-break (rep+ (alt newline-lit return-lit)))

;(def ws (constant-semantics (rep* (alt space tab line-break)) :ws))

(def ws (rep* (alt space tab line-break)))

(def comment-start (lit-conc-seq "<!--"))
 
(def comment-end (lit-conc-seq "-->"))

(defn test-set [& args]
  (set (mapcat (fn [value]
                 (cond (sequential? value)
                        (map char (range (int (first value)) (inc (int (second value)))))
                        (char? value) [value])) args)))

(def lower-case (term #(contains? (test-set [\a \z]) %)))

(def lower-case-plus-hyphen (term #(contains? (test-set [\a \z] \-) %)))

(def tmpl-identifier (semantics (conc lower-case (rep* lower-case-plus-hyphen)) (fn [result] (apply str (flatten result)))))

;(def tmpl-var
 ; (conc comment-start ws (lit-conc-seq "TMPL_VAR") ws tmpl-identifier ws comment-end))

;(def tmpl-var
 ; (conc comment-start ws (lit-conc-seq "TMPL_VAR") ws tmpl-identifier ws comment-end))

(def tmpl-var
  (complex [_ comment-start
            _ ws
            _ (lit-conc-seq "TMPL_VAR")
            _ ws
            identifier tmpl-identifier
            _ ws
            _ comment-end]
           { :tmpl-var identifier }))

(def tmpl-loop-start
  (complex [_ comment-start
            _ ws
            _ (lit-conc-seq "TMPL_LOOP")
            _ ws
            identifier tmpl-identifier
            _ ws
            _ comment-end]
           identifier))
 
(def tmpl-loop-end (conc comment-start ws (lit-conc-seq "/TMPL_LOOP") ws comment-end))

;(def non-tmpl (except (rep* anything) (conc comment-start ws (lit-conc-seq "TMPL_"))))

;(def non-tmpl (rep* (alt (except anything (lit \<)) (conc (lit \<) (except anything (lit \!))))))

;(def non-tmpl (rep* (alt (except anything (lit \<)) (conc (lit \<) (except anything (lit \!))) (except (conc comment-start ws) (conc comment-start ws (lit-conc-seq "TMPL_"))))))

;; (def non-tmpl (semantics
;;                (rep* (alt
;;                       (except anything (lit \<))
;;                       (conc (lit \<) (except anything (lit \!)))
;;                       (complex [subproduct (conc comment-start ws), _ (not-followed-by (alt (lit-conc-seq "TMPL_") (lit-conc-seq "/TMPL_")))]
;;                                subproduct)))
;;                (fn [result] { :text (apply str (flatten result))})))

(def non-tmpl (semantics
               (rep* (alt
                      (except anything (lit \<))
                      (complex [subproduct (lit \<), _ (not-followed-by (conc (lit-conc-seq "!--") ws (alt (lit-conc-seq "TMPL_") (lit-conc-seq "/TMPL_"))))]
                               subproduct)))
               (fn [result] { :text (apply str (flatten result))})))

(declare tmpl-loop)

(defn debug [subrule]
  (complex [init-state get-state, subproduct subrule, state get-state, _ (effects (println init-state " -> " state))]
           subproduct))

(defn debug2 [name subrule]
  (complex [init-state get-state, _ (effects (println name ": " init-state)), subproduct subrule, state get-state, _ (effects (println name ": " state))]
           subproduct))

(def stmt
  (alt
   (debug2 "non-tmpl" non-tmpl)
   (debug2 "tmpl-var" tmpl-var)
   (debug2 "tmpl-loop" tmpl-loop)))

(def tmpl-loop
  (complex [loop-identifier tmpl-loop-start
             loop-text stmt
             _ tmpl-loop-end]
            { :loop loop-identifier :loop-text loop-text}))

(def text
  (rep* stmt))

(defn t [rule] (rule-match rule prn prn { :remainder "<html><head><!-- this is foo --></head><body><!-- TMPL_LOOP foo --><!-- TMPL_VAR hello --><!-- /TMPL_LOOP --></body></html>" }))
(defn u [rule] (rule-match rule prn prn { :remainder "<html><head><!-- this is foo --></head><body><!-- TMPL_VAR hello --></body></html>" }))
  
(defn ^String substring?
  "True if s contains the substring."
  [substring ^String s]
  (.contains s substring))

(defn transform [stream transforms]
  stream)

(deftest tmpl-var-test ()
  (with-in-str "<html><head/><body><!-- TMPL_VAR hello --></body></html>"
    (is (= (transform *in* { :hello "hello world!" }) "<html><head/><body>hello world!</body></html>"))))

(run-tests)

(defn first-match [m]
  (if (coll? m) (first m) m))

(defn match [regex text]
  (let [m (first-match (re-find (re-pattern regex) text))]
    (if (nil? m)
      [0 0]
      (let [ind (.indexOf text m) len (.length m)]
        [ind (+ ind len)]))))

(defn parse-tmpl-var [data]
  (let [result (re-find (re-pattern "^<!--\\s+TMPL_VAR\\s+([a-z][a-z0-9]*)\\s+-->") data)]
    (if result
      [ [ :tmpl-var (second result) ], (subs data (count (first result))) ]
      nil)))
  
(defn parse-tmpl-loop [data]
  (let [result (re-find (re-pattern "^<!--\\s+TMPL_LOOP\\s+([a-z][a-z0-9]*)\\s+-->") data)]
    (if result
      [ [ :tmpl-loop (second result) ], (subs data (count (first result))) ]
      nil)))
  
(defn parse-tmpl-end-loop [data]
  (let [result (re-find (re-pattern "^<!--\\s+/TMPL_LOOP\\s+-->") data)]
    ;(println parse-tmpl-end-loop ": " data " -> " result)
    (if result
      [ [ :tmpl-end-loop nil ], (subs data (count result)) ]
      nil)))
  
(defn parse-tmpl-directive [data]
  (loop [directive-parsers [parse-tmpl-var parse-tmpl-loop parse-tmpl-end-loop]]
    (assert directive-parsers) ; should never run out of parsers
    (let [result ((first directive-parsers) data)]
      (if (nil? result)
        (recur (rest directive-parsers))
        result))))

(defn parse-tmpl [init-data]
  (loop [result []
         data init-data]
    (if (> (count data) 0)
      (let [[start, finish] (match "<!--\\s+(TMPL_(IF|ELSE|((VAR|LOOP)\\s+[a-z][a-z\\-0-9]*))|/TMPL_(IF|LOOP))\\s+-->" data)]
        ;(println data " " start " " finish)
        (cond (> start 0)    ; collect plain text up to tmpl directive
              (recur (conj result [ :text (subs data 0 (- start 1)) ])
                     (subs data start))
              (and (= start 0) (= finish 0)) ; remainder is plain text
              (recur (conj result [ :text data ])
                     "")
              (and (= start 0) (> finish 0))
              (let [[directive, next-data] (parse-tmpl-directive data)]
                (recur (conj result directive) next-data))
              t (assert false "Oops!")))
      result)))


(defn v []
  (let [tmpl "<html><head><!-- this is foo --></head><body><!-- TMPL_LOOP foo --><!-- TMPL_VAR hello --><!-- /TMPL_LOOP --></body></html>"]
    (parse-tmpl tmpl)))

(defn w []
  (let [tmpl "<html><head><!-- this is foo --></head><body><!-- TMPL_VAR hello --></body></html>"]
    (parse-tmpl tmpl)))

(defn vv []
  (loop [compiled-output nil
         context (gensym)
         tokens (w)]
    (if tokens
      (let [[key value] (first tokens)]
        (println "compiled-output:" compiled-output "key:" key ", value:" value)
        (cond (= key :text)
          (recur (conj compiled-output `(println ~value)) context (next tokens))
          (= key :tmpl-var)
          (recur (conj compiled-output `(get ~context ~value)) context (next tokens))
          :else (recur compiled-output context (next tokens))))
      `(fn [~context] ~@(reverse compiled-output)))))

(defstruct context :compiled-output :context :context-symbol)

(defn create-context-stack []
  [(struct context [] (gensym) nil)])

(defn push-context-stack [ctx context-symbol]
  (conj ctx (struct context [] (gensym) context-symbol)))

(defn pop-context-stack [context]
  (pop context))

(defn peek-compiled-output [context]
  (:compiled-output (peek context)))

(defn peek-context [context]
  (:context (peek context)))

(defn peek-previous-context [context]
  (peek-context (pop-context-stack context)))

(defn peek-context-symbol [context]
  (:context-symbol (peek context)))

(defn push-compiled-output [context value]
  (let [ele (peek-compiled-output context)
        old-struct (peek context)]
    (conj (pop context) (assoc old-struct :compiled-output (conj ele value)))))

(defn vvv []
  (loop [context (create-context-stack)
         tokens (v)]
    (if tokens
      (let [[key value] (first tokens)]
        (println "compiled-output:" (peek-compiled-output context) "key:" key ", value:" value)
        (cond (= key :text)
              (recur (push-compiled-output context `(println ~value)) (next tokens))
              (= key :tmpl-var)
              (recur (push-compiled-output context `(get ~(peek-context context) ~(keyword value))) (next tokens))
              (= key :tmpl-loop)
              (recur (push-context-stack context value) (next tokens))
              (= key :tmpl-end-loop)
              (let [loop-compiled-output (peek-compiled-output context)
                    context-symbol (peek-context-symbol context)
                    compiled-output `(doseq [~(peek-context context) (get ~(peek-previous-context context) ~(keyword (peek-context-symbol context)))]
                                       ~loop-compiled-output)]
                (recur (push-compiled-output (pop-context-stack context) compiled-output) (next tokens)))
              :else (recur context (next tokens))))
      `(fn [~(peek-context context)] ~@(peek-compiled-output context)))))

