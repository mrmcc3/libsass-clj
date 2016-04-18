(ns libsass.build
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [io.bit3.jsass Options OutputStyle]))

;; ----------------------------------------------------------------------------
;; helpers

(defn uri [f]
  (-> f io/file .toURI))

(defn relativize [parent child]
  (-> (uri parent) (.relativize (uri child)) .getPath))

(defn sass-file? [f]
  (and (or (.endsWith (.getName f) ".scss")
           (.endsWith (.getName f) ".sass"))
       (.isFile f)))

(defn sass-entry-file? [f]
  (and (not (.startsWith (.getName f) "_"))
       (sass-file? f)))

(defn css-ext [f]
  (let [path (.getAbsolutePath (io/file f))]
    (str/replace-first path #"\.(scss|sass)$" ".css")))

;; ----------------------------------------------------------------------------
;; options

(defn jsass-options [opts]
  (let [{:keys [source-comments source-map output-style
                precision]} opts
        {:keys [embed contents root file]} source-map
        opts (Options.)]
    (when precision (.setPrecision opts precision))
    (when source-comments (.setSourceComments opts true))
    (when-not source-map (.setOmitSourceMapUrl opts true))
    (when source-map
      (when embed (.setSourceMapEmbed opts true))
      (when contents (.setSourceMapContents opts true))
      #_(when root (.setSourceMapRoot opts (uri root)))
      #_(when file (.setSourceMapFile opts (uri file))))
    (.setOutputStyle
      opts
      (case output-style
        :nested OutputStyle/NESTED
        :compact OutputStyle/COMPACT
        :compressed OutputStyle/COMPRESSED
        OutputStyle/EXPANDED))
    opts))

;; ----------------------------------------------------------------------------
;; compilation

(def ^io.bit3.jsass.Compiler compiler (io.bit3.jsass.Compiler.))

(defn compile-sass [^Options opts [in out]]
  (.setIsIndentedSyntaxSrc opts (.endsWith in ".sass"))
  (let [out-uri (uri (if out out (css-ext in)))
        result (.compileFile compiler (uri in) out-uri opts)]
    [in {:css (.getCss result) :map (.getSourceMap result) :path out}]))

(defn build-css [paths opts]
  (let [xform (map (partial compile-sass (jsass-options opts)))]
    (into {} xform paths)))

;; ----------------------------------------------------------------------------
;; collect sass files. (sources -> in/out map)

(defn output-path [source target input-file]
  [(.getAbsolutePath input-file)
   (when target
     (->> input-file (relativize source) (io/file target) css-ext))])

(defn gen-paths [[source-path target]]
  (let [source (io/file source-path)]
    (cond
      (sass-entry-file? source)
      (output-path (.getParentFile source) target source)
      (.isDirectory source)
      (into {}
            (comp (filter sass-entry-file?)
                  (map (partial output-path source target)))
            (file-seq source))
      :else {})))

(defn input->paths [input {:keys [output-dir]}]
  (transduce
    (map gen-paths)
    merge
    (cond
      (vector? input) (zipmap input (repeat output-dir))
      (map? input) input
      :else {input output-dir})))

;; ----------------------------------------------------------------------------
;; build

(defn build
  ([input] (build input {}))
  ([input opts]
   (let [result (-> input (input->paths opts) (build-css opts))]
     (doseq [[_ {:keys [css path]}] result]
       (when path
         (doto (io/file path) io/make-parents (spit css))))
     result)))

;; ----------------------------------------------------------------------------
;; clean

(defn clean-empty-dirs [root]
  (when (.isDirectory root)
    (doseq [child (.listFiles root)]
      (clean-empty-dirs child))
    (io/delete-file root true)))

(defn clean [input opts]
  (when (map? input)
    (doseq [[_ path] (input->paths input opts)]
      (let [f (io/file path)]
        (when (.exists f) (io/delete-file f))))
    (doseq [[_ path] input]
      (clean-empty-dirs (io/file path)))))
