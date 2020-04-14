(ns test
  (:require [clojure.core.async :refer [<!!]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [kintone-client.app :as app]
            [kintone-client.authentication :as auth]
            [kintone-client.connection :as conn]
            [kintone-client.record :as record]))

(def conf (edn/read-string (slurp "dev-resources/config.edn")))

(def auth (auth/new-auth (:auth conf)))

(def conn (conn/new-connection {:auth auth
                                :domain (:domain conf)}))

(def app (:app conf))

(def string-field-code :文字列__1行_)

(def attachment-file-field-code :添付ファイル)

(defn delete-all-record [conn app]
  (record/delete-all-records-by-query conn app ""))

(defmacro with-cleanup [& body]
  `(try ~@body
        (finally
          (<!! (delete-all-record conn app)))))

(defn pp
  [x]
  (println x)
  x)

;; TODO: throws weird error on Cursive
;; Error handling response - class java.lang.IndexOutOfBoundsException: Wrong line: 140. Available lines count: 140
;; use (run-tests 'test)

(deftest file-upload-filename-mojibake-test
  (testing "japanese filename"
    (with-cleanup
     (let [upload-file-key (-> (<!! (record/file-upload conn (io/file "dev-resources/日本語ファイル名.txt")))
                               :res
                               :fileKey)
           record-id (-> (<!! (record/add-record conn app {string-field-code {:value "file name test"}
                                                           attachment-file-field-code {:value [{:fileKey upload-file-key}]}}))
                         :res
                         :id)
           file-name (-> (<!! (record/get-record conn app record-id))
                         :res
                         :record
                         attachment-file-field-code
                         :value
                         first
                         :name)]
       (is (= "日本語ファイル名.txt" file-name)))))
  (testing "ascii filename"
    (with-cleanup
      (let [upload-file-key (-> (<!! (record/file-upload conn (io/file "dev-resources/ascii-filename.txt")))
                                :res
                                :fileKey)
            record-id (-> (<!! (record/add-record conn app {string-field-code {:value "file name test"}
                                                            attachment-file-field-code {:value [{:fileKey upload-file-key}]}}))
                          :res
                          :id)
            file-name (-> (<!! (record/get-record conn app record-id))
                          :res
                          :record
                          attachment-file-field-code
                          :value
                          first
                          :name)]
        (is (= "ascii-filename.txt" file-name))))))

(deftest get-record-test
  (with-cleanup
    (let [id (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "ほげ"}}))
                 :res
                 :id)]
      (is (= nil (:err (<!! (record/get-record conn app id))))))))

(deftest get-all-records-test
  (with-cleanup
    (let [_ (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "ほげ1"}}))
                  :res
                  :id)
          _ (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "ほげ2"}}))
                  :res
                  :id)
          _ (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "ほげ3"}}))
                  :res
                  :id)
          res (<!! (record/get-all-records conn app))]
      (is (= nil (:err res)))
      (is (= (set ["ほげ1" "ほげ2" "ほげ3"])
             (->> res :res :records (map #(-> % :文字列__1行_ :value)) set))))))

(deftest get-records-by-query-test
  (with-cleanup
    (let [_ (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "ほげ1"}}))
                :res
                :id)
          _ (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "ほげ2"}}))
                :res
                :id)
          _ (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "ほげ3"}}))
                :res
                :id)]
      (is (= nil (:err (<!! (record/get-records-by-query conn app)))))
      (is (= "3" (-> (<!! (record/get-records-by-query conn app))
                     :res
                     :totalCount)))
      (is (= nil (:err (<!! (record/get-records-by-query conn app {:fields [:$id]})))))
      (is (= nil (:err (<!! (record/get-records-by-query conn app {:fields [:作成者]
                                                                   :query "order by 作成者 asc"})))))
      (is (= nil (:err (<!! (record/get-records-by-query conn app {:fields [:作成者]
                                                                   :query "order by 作成者"})))))
      (is (= 1 (-> (<!! (record/get-records-by-query conn app {:query "order by 作成者 limit 1"}))
                   :res
                   :records
                   count)))
      (is (= nil (:err (<!! (record/get-records-by-query conn app {:query "offset 1"})))))
      (is (= ["ほげ3" "ほげ2" "ほげ1"]
             (->> (<!! (record/get-records-by-query conn app {:query "order by 文字列__1行_ desc"}))
                  :res
                  :records
                  (mapv #(-> % :文字列__1行_ :value)))))
      (is (= ["ほげ1" "ほげ2" "ほげ3"]
             (->> (<!! (record/get-records-by-query conn app {:query "order by 文字列__1行_ asc"}))
                  :res
                  :records
                  (mapv #(-> % :文字列__1行_ :value))))))))

(deftest get-record-by-cursor-test
  (with-cleanup
    (let [_ (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "ほげ1"}}))
                :res
                :id)
          _ (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "ほげ2"}}))
                :res
                :id)
          _ (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "ほげ3"}}))
                :res
                :id)
          cursor (:res (<!! (record/create-cursor conn app)))]
      (try
        (let [res (<!! (record/get-records-by-cursor conn cursor))]
          (is (= nil (:err res)))
          (is (= 3 (-> res :res :records count))))
        (finally
          (<!! (record/delete-cursor conn cursor)))))))

(deftest add-record-test
  (with-cleanup
    (let [res (<!! (record/add-record conn app {:文字列__1行_  {:value "ほげ"}}))]
      (is (= nil (:err res)))
      (is (= 1 (-> (<!! (record/get-all-records conn app))
                   :res
                   :records
                   count))))))

(deftest add-records-test
  (with-cleanup
    (let [res (<!! (record/add-records conn app [{:文字列__1行_ {:value "ほげ1"}}
                                                 {:文字列__1行_ {:value "ほげ2"}}]))]
      (is (= nil (:err res)))
      (is (= 2 (-> (<!! (record/get-all-records conn app))
                   :res
                   :records
                   count))))))

(deftest add-all-records-test
  (with-cleanup
    (let [res (<!! (record/add-all-records conn app [{:文字列__1行_ {:value "ほげ1"}}
                                                     {:文字列__1行_ {:value "ほげ2"}}]))]
      (is (= nil (:err res)))
      (is (= 2 (-> (<!! (record/get-all-records conn app))
                   :res
                   :records
                   count))))))

(deftest update-record-test
  (with-cleanup
    (let [id (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "あああ"}}))
                 :res
                 :id)
          res (<!! (record/update-record conn app {:id id
                                                   :record {:文字列__1行_ {:value "いいい"}}}))]
      (is (= nil (:err res)))
      (is (= "いいい"
             (-> (<!! (record/get-record conn app id))
                 :res
                 :record
                 :文字列__1行_
                 :value))))))

(deftest update-records-test
  (with-cleanup
    (let [id1 (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "あああ"}}))
                  :res
                  :id)
          id2 (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "あああ"}}))
                  :res
                  :id)
          res (<!! (record/update-records conn app [{:id id1
                                                     :record {:文字列__1行_ {:value "いいい"}}}
                                                    {:id id2
                                                     :record {:文字列__1行_ {:value "ううう"}}}]))]
      (is (= nil (:err res)))
      (is (= "いいい"
             (-> (<!! (record/get-record conn app id1))
                 :res
                 :record
                 :文字列__1行_
                 :value)))
      (is (= "ううう"
             (-> (<!! (record/get-record conn app id2))
                 :res
                 :record
                 :文字列__1行_
                 :value))))))

(deftest update-all-records-test
  (with-cleanup
    (let [id1 (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "あああ"}}))
                  :res
                  :id)
          id2 (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "あああ"}}))
                  :res
                  :id)
          res (<!! (record/update-all-records conn app [{:id id1
                                                         :record {:文字列__1行_ {:value "いいい"}}}
                                                        {:id id2
                                                         :record {:文字列__1行_ {:value "ううう"}}}]))]
      (is (= nil (:err res)))
      (is (= "いいい"
             (-> (<!! (record/get-record conn app id1))
                 :res
                 :record
                 :文字列__1行_
                 :value)))
      (is (= "ううう"
             (-> (<!! (record/get-record conn app id2))
                 :res
                 :record
                 :文字列__1行_
                 :value))))))

(deftest file-upload-test
  (with-cleanup
    (let [file-key (-> (<!! (record/file-upload conn (io/file "dev-resources/aa.txt")))
                       :res
                       :fileKey)
          res (<!! (record/add-record conn app {:添付ファイル {:value [{:fileKey file-key}]}}))]
      (is (= nil (:err res))))))

(deftest file-download-test
  (with-cleanup
    (let [upload-file-key (-> (<!! (record/file-upload conn (io/file "dev-resources/aa.txt")))
                              :res
                              :fileKey)
          record-id (-> (<!! (record/add-record conn app {:添付ファイル {:value [{:fileKey upload-file-key}]}}))
                        :res
                        :id)
          download-file-key (-> (<!! (record/get-record conn app record-id))
                                :res
                                :record
                                :添付ファイル
                                :value
                                first
                                :fileKey)
          res (<!! (record/file-download conn download-file-key))]
      (is (= nil (:err res))))))

(deftest bulk-request-test
  (with-cleanup
    (let [id (-> (<!! (record/add-record conn app {:文字列__1行_ {:value "あああ"}}))
                 :res
                 :id)
          res (<!! (record/bulk-request conn
                                        [(record/add-record app {:文字列__1行_ {:value "いいい"}})
                                         (record/update-record app {:id id
                                                               :record {:文字列__1行_ {:value "ううう"}}})]))
          records (<!! (record/get-all-records conn app))]
      (is (= nil (:err res)))
      (is (= (set ["いいい" "ううう"])
             (->> records :res :records (map #(-> % :文字列__1行_ :value)) set))))))

;; NOTE: ensure app name is "kintone-clj test"
(deftest get-app-test
  (with-cleanup
    (let [{:keys [res err]}  (<!! (app/get-app conn app))]
      (is (nil? err))
      (is (= "kintone-clj test" (:name res)))
      (is (= (str app) (:appId res))))))

;; NOTE: ensure app has these fields
(deftest get-form-test
  (with-cleanup
    (let [{:keys [res err]} (<!! (app/get-form conn app))
          [number-field single-line-text-field file-field
           :as props] (some->> res :properties (sort-by :code))
          test-fields [:type :label :code]]
      (is (nil? err))
      (is (= 3 (count props)))
      (is (= {:type "NUMBER" :code "数値" :label "数値"}
             (select-keys number-field test-fields)))
      (is (= {:type "SINGLE_LINE_TEXT" :code "文字列__1行_" :label "文字列 (1行)"}
             (select-keys single-line-text-field test-fields)))
      (is (= {:type "FILE" :code "添付ファイル" :label "添付ファイル"}
             (select-keys file-field test-fields))))))

(comment
 (run-tests 'test))

