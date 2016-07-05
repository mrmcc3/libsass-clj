(ns libsass.build
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [io.bit3.jsass Options OutputStyle]
           [java.net URI]))

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

(defn jsass-options [out opts]
  (let [{:keys [sass precision source-comments omit-source-map-url
                source-map source-map-contents source-map-embed
                output-style fns]} opts
        opts (Options.)]
    (when sass (.setIsIndentedSyntaxSrc opts true))
    (when precision (.setPrecision opts precision))
    (when source-comments (.setSourceComments opts true))
    (when omit-source-map-url (.setOmitSourceMapUrl opts true))
    (when source-map (.setSourceMapFile opts (URI. (str out ".map"))))
    (when source-map-contents (.setSourceMapContents opts true))
    (when source-map-embed (.setSourceMapEmbed opts true))
    (when fns (.add (.getFunctionProviders opts) fns))
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

(defn compile-sass [opts [in out]]
  (let [out-uri (uri (if out out (css-ext in)))
        opts (jsass-options out-uri (assoc opts :sass (.endsWith in ".sass")))
        result (.compileFile compiler (uri in) out-uri opts)]
    [in {:css (.getCss result) :map (.getSourceMap result) :path out}]))

(defn build-css [paths opts]
  (let [xform (map (partial compile-sass opts))]
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
     (doseq [[_ {:keys [css map path]}] result]
       (when path
         (doto (io/file path) io/make-parents (spit css))
         (when (:source-map opts)
           (spit (io/file (str path ".map")) map))))
     result)))

(defn clean-empty-dirs [root]
  (when (.isDirectory root)
    (doseq [child (.listFiles root)]
      (clean-empty-dirs child))
    (io/delete-file root true)))

(defn clean [input opts]
  (doseq [[_ path] (input->paths input opts)]
    (let [f (io/file path)]
      (when (.exists f) (io/delete-file f))))
  (if (map? input)
    (doseq [[_ path] input]
      (clean-empty-dirs (io/file path)))
    (clean-empty-dirs (-> opts :output-dir io/file))))
