(ns clojure-first.core
  (:gen-class)
  (:require [mount.core :as mount]
            [hugsql.core :as hugsql]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.jdbc :as sql]
            [clojure.string :as string]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(def cardnumbers [2 3 4 5 6 7 8 9 10 "J" "Q" "K" "A"])
(def cardtypes ["Hearts" "Diamonds" "Clubs" "Spade"])
(def cardshorttypes ["HT" "DM" "CL" "SP"])
(def cardTypes [{:longName "of Hearts" :shortName "HT"} 
                {:longName "of Diamonds" :shortName "DM"} 
                {:longName "of Clubs" :shortName "CL"} 
                {:longName "of Spade" :shortName "SP"}])
(def headerCSV "matchnumbers,picktimes,pickedcards,remainingcards")

(defn createDeckOfOneCards
[cardtype]
(loop [cardnumber 0 deckofcard []]
  
   (if (> cardnumber 12)
     deckofcard
     (recur (inc cardnumber) (conj deckofcard (str (get cardnumbers cardnumber) " of " cardtype))))
   )
)

(defn createDeckOfCards
[]
(loop [cardtype 0 deckofcard []]
  (if (> cardtype 3)
    deckofcard
    (recur (inc cardtype) (into deckofcard (createDeckOfOneCards (get cardtypes cardtype))))))
)

(defn replaceLongToShortCardType
[cards longName shortName]
(reduce (fn [result part] (conj result (clojure.string/replace part longName shortName))) [] cards)
)

;(:longName (get cardTypes 1))

(defn replaceLongToShortCardTypes
[cards cardTypes]
(reduce (fn [result part] (replaceLongToShortCardType result (:longName part) (:shortName part))) cards cardTypes)
)


(defn replaceShortToLongCardType
[cards longName shortName]
(reduce (fn [result part] (conj result (clojure.string/replace part shortName longName))) [] cards)
)

(defn replaceShortToLongCardTypes
[cards cardTypes]
(reduce (fn [result part] (replaceShortToLongCardType result (:shortName part) (:longName part))) cards cardTypes)
)


;(createDeckOfCards)
;(replaceLongToShortCardType (createDeckOfCards) "of Hearts" "HT")

;(replaceLongToShortCardTypes (createDeckOfCards) cardTypes)


(defn shuffleCards
[DeckOfCards]
(shuffle DeckOfCards)
)

;(shuffleCards (createDeckOfCards))

(defn pickOneCard
[position DeckOfCards]
(if (or (> position 51) (< position 0))
  (println "Out of DeckCard")
  (do (get DeckOfCards position)))
)

;(pickOneCard 11 (createDeckOfCards))

(defn remainingOneCard
[position DeckOfCards]
(if (or (> position 51) (< position 0))
  (println "Out of DeckCard")
  (do (into (into [] (take position DeckOfCards)) (into [] (drop (+ position 1) DeckOfCards)) )))
)

;(remainingOneCard 11 (createDeckOfCards))

(defn createSeqVector
[limit]
(loop [initial 0 result []]
  (if (> initial (- limit 1))
    result
    (recur (inc initial) (conj result initial))))
)

;(createSeqVector 20)

(defn get10RandomNumbers
[seqvector]
(take 10 (shuffle seqvector))
)

;(get10RandomNumbers (createSeqVector 20))

(defn get10RandomNumbers
[seqvector]
(take 10 (shuffle seqvector))
)

;(get10RandomNumbers (createSeqVector 20))

(defn pick10RandomCards
[DeckOfCards]
(loop [[part & remaining] (get10RandomNumbers (createSeqVector (count DeckOfCards))) result []]
  (if (empty? remaining)
    result
    (recur remaining (conj result (get DeckOfCards part)))))
)

(defn pick10RandomCardsV2
[DeckOfCards]
(reduce (fn [result part] (conj result (get DeckOfCards part))) [] (get10RandomNumbers (createSeqVector (count DeckOfCards))))
)

;(pick10RandomCardsV2 (createDeckOfCards))

(defn remainingAftergetting10Cards
[TenRandomCard DeckOfCards]
(reduce (fn [cards card] (remainingOneCard (.indexOf cards card) cards)) DeckOfCards TenRandomCard)
)

;(remainingAftergetting10Cards (pick10RandomCardsV2 (createDeckOfCards)) (createDeckOfCards))

(def spec
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     ":memory:"})

(def db-uri "jdbc:sqlite::memory:")

(declare db)

(defn on-start []
  (let [spec {:connection-uri db-uri}
        conn (jdbc/get-connection spec)]
    (assoc spec :connection conn)))

(defn on-stop []
  (-> db :connection .close)
  nil)

(mount/defstate
  ^{:on-reload :noop}
  db
  :start (on-start)
  :stop (on-stop))

(mount/start #'db)

;(mount/stop #'db)

(jdbc/execute! db "create table playingcards (id integer,matchnumber integer,picktimes integer,pickedcards text,remainingcards text)")


(defn getMaxMatchNumber
[]
(or (:matchnumber (first (sql/query db ["select max(matchnumber) as matchnumber from playingcards"]))) 0)
)


(def MaxMatchNumber
#(+ (getMaxMatchNumber) %)
)

;(MaxMatchNumber)
;(getMaxMatchNumber)
;(MaxMatchNumber 1)

(defn getMaxID
[]
(or (:id (first (sql/query db ["select max(id) as id from playingcards"]))) 0)
)

;(getMaxID)
;(+ (getMaxID) 1)

(defn insertDB
[pickedCards remainingCards picktimes]
(jdbc/insert! db :playingcards {:id (+ (getMaxID) 1) :matchnumber (MaxMatchNumber (or (and (= picktimes 1) 1) 0)) :picktimes picktimes :pickedcards pickedCards :remainingcards remainingCards})
(println "Inserted 1 record to database successfully !")
)

;(getMaxMatchNumber)

(defn insertCSV
[picktimes pickedcards remainingcards]
(with-open [writer (io/writer "out-file.csv" :append true)]
  (csv/write-csv writer
                 [[(MaxMatchNumber 0) picktimes pickedcards remainingcards]]))
)


(defn lazy-file-lines [file]
  (letfn [(helper [rdr]
                  (lazy-seq
                    (if-let [line (.readLine rdr)]
                      (into (cons line (helper rdr)) "\n")
         
                      (do (.close rdr) nil))))]
         (helper (clojure.java.io/reader file))))

;(lazy-file-lines "out-file.csv")

;(println (cons headerCSV (lazy-file-lines "out-file.csv")))


(defn -main
[& args]
  (println "Showing previous result: \n")
  (println (cons headerCSV (lazy-file-lines "out-file.csv")))
  (let [x (createDeckOfCards)] 
    (println (str "Creating Deck Of Cards: \n" x)) 
    (let [y (shuffleCards x)]
      (println (str "Shuffling Deck Of Cards: \n" y))
      (println "Picking up the first 10 random cards....")
      (let [z (pick10RandomCardsV2 y)]
        (println z) (println (remainingAftergetting10Cards z y))
        (insertDB (replaceLongToShortCardTypes z cardTypes) (replaceLongToShortCardTypes (remainingAftergetting10Cards z y) cardTypes) 1)
        (insertCSV 1 (replaceLongToShortCardTypes z cardTypes) (replaceLongToShortCardTypes (remainingAftergetting10Cards z y) cardTypes))
        (loop [y1 (remainingAftergetting10Cards z y) picktimes 2]
          (println (str "Click \"p\" to pick up 10 random cards at the " picktimes " times...."))
          (let [pid (read-line)]
            (if (= pid "p")
              (if (< (count y1) 10)
                (println "Remaining cards are less than 10 cards. Finished picking cards !")
                (let [z1 (pick10RandomCardsV2 y1)]
                  (println (str "Picked cards at the " picktimes " times: \n" z1)) 
                  (println (str "Remaining cards at the " picktimes " times: \n" (remainingAftergetting10Cards z1 y1)))
                  (insertDB (replaceLongToShortCardTypes z1 cardTypes) (replaceLongToShortCardTypes (remainingAftergetting10Cards z1 y1) cardTypes) picktimes)
        (insertCSV picktimes (replaceLongToShortCardTypes z1 cardTypes) (replaceLongToShortCardTypes (remainingAftergetting10Cards z1 y1) cardTypes))
                  (recur (remainingAftergetting10Cards z1 y1) (inc picktimes))
                )
              )
              (println "Finished picking cards !")
            )
          )
        )  
      )
    )
  )
  (println "Showing all result from database: \n")
  (println (sql/query db ["select * from playingcards"]))
)  

;(-main)


;(sql/query db ["select * from playingcards where matchnumber=3"])

