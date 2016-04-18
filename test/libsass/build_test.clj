(ns libsass.build-test
  (:require [libsass.build :as sass]
            [clojure.test :refer [deftest is]]))

;; TODO

(deftest compile-single
  (let [r (sass/build "test/sass-files/a.scss" {:output-style :compressed})]
    (is (= (-> r vals first :css) ".b{color:white}.a{color:black}\n"))))
