# Gese
![Gese](logo.png "Gese")

[Описание API](https://github.com/cromlehg/Gese/blob/master/API.md)

### Терминология
Пост в идеалле это абстактная сущность, от которой наследуются сущности: отзыв и статья.

### Новая экономика

#### Три валюты:

##### __Aar __
Валюта за которую можно купить Power.
Курс Token = 1 Dollar пока нет торговли на внешней бирже. Обмен на dollar сразу. Обратный только после 5 дней.
Лимитированный выпуск (300 млн)
Можно переводить другим пользователям.

##### __Power (Power)__ 
Валюта в которой определяется сила вознаграждения от лайка/поста.
Курс 1 token = 10 Power. Power можно купить только за Token. Покупка происходит сразу. Продать можно только за Gese и вывести в течение 100 дней с момента продажи.
Эмиссия безконтрольная. Система сама эмитирует когда надо.

##### __Dollar (GDR)__
Валюта для начисления вознаграждений. Валюта для компенсации внешних колебаний. Основная денежная единица расчета на платформе. В этой валюте происходит начисление вознаграждений и инвестирование в мечту.
Эмиссия безконтрольная. Система сама эмитирует когда надо.
Можно переводить другим пользователям.

#### Начальная экономика
* Изначально будет выпущено около 300 млн. токенов Gese. 20% из них в резерв платформе.
* Зарегистрировавшемуся пользователю начилсяются 50 Power. Или все же он регистрируется и вводит token с ICO как привелегия только тем кто купил токены?

#### Экономика лайка 
Лайк может быть поставлен на пост, мечту или комментарий не более одного раза. 
* Нельзя лайкать свои собственнные посты и комментарии
* Суммарное количество лайков не более 100 в день
* Aвтор лайка получает 0.5% от __rewards__ 
* Автор комментария, котоый лайкают получает 75% от __rewards__
* Автор поста, котоый лайкают получает 75% от __rewards__ и начислние идет по выбраной схеме вознаграждения

#### Экононмика размещения поста или мечты
* Не более 15 постов
* Пользователь может выбрать получить вознаграждение как 50/50 gese power/gese dollars так и чисто в gese power (т.е. пересчет пула наград будет по курсу 1 GRD к 10 GP)

#### Экономика размещение комментария
* ___ограничили пока 20-ю, иначе так можно бесконечно мусорить___

#### Инвестирования в мечту
* Производится в Gese Dollars и полностью и безвозвратно начисляются на счет автора мечты. При этом отображается индикатор.

#### Продвижение
* Любой пост можно продвинуть внесением gese dollars. При продвижении dollars сжигаются, а к promotion рейтингу поста добавляется соответствующая сумма. Продвигаемые посты сортируются в категории promotion.

#### Пул Rewards
Сумма power на начало дня пользователя который создает активность/15

## Инфляционная экономика
Положения
* Все награды выполняются в dollars и power
* Генерация токенов идет с учетом инфляции
### Механимз инфляции
#### Начальные данные
* Дата запуска платформы
* Изначально существующее число токенов
* Минимальная годовая ставка инфляции
* Начальная годовая ставка инфляции
* Шаг уменьшения инфляции в день
#### Алгоритм
* Вычисляем дневную ставку инфляции на текущий момент
* Генерируем системе DREAM
* Устанавливаем курс обмена за счет траназкций конвертации предыдущего???
* По курсу обмена предыдущего дня + 50% (стразовка) предлагаем менять токены


#### Либо считать - пул rewards пользователя * ограничения = сколько максимально может нагенерировать пользователь

### Dev запуск
* Склонировать репозиторий
* Создать базу данных - скрипт dev/sql/create_db.sql
* Создать таблицы в базе - скрипт dev/sql/create_tables.sql
* Создать данные в базе - скрипт dev/sql/data.sql
* Скопировать conf/example_application.conf в conf/application.conf
* Исправить conf/application.conf в соответсвие со своей базой данных
* В папке проекта выполнить sbt
* В коносле sbt выполнить compile
* В коносле sbt выполнить eclipse (теперь можно импортировать проект в scala-IDE)
* В коносле sbt выполнить ~run
* В браузере ввести localhost:9000

### Описание биллинга
Токен ERC20 в отличие от специального токена графена не требует сложного входа, поскольку является широко-используемым стандартом.
Технически на плафторме не будут вестись операции с токенов ERC20. Поскольку это дорого и долго. Будет счет, агрегирующий ликвидность. Когда пользователь регистрируется и заводит токены, то токены падают на единый счет. А в базе у аккаунта пользователя прописываются балансы. Далее плафторма ведет все расчеты по записям в базе. Таким образом токен сможет торговаться и на бирже и на платформе. 

#### Ввод
* На ICO пользователь получил токены
* Пользователь регистрируется на плафторме
* Нажимает кнопку "ввести токены"
* Появляется заранее сегенрированный адрес эфира и предложение отправить на этот адресс эфир
* Формируется заявка, ожидающая оплаты при нажатии "я отправил"

### Основные страницы
* ___Главная___ - вывод списка 
* ___Регистрация__ 
* ___Авторизация___
* ___Профиль___ - баланс (возможность вывода/ввода и перевода токенов платформы), мечты, аватар, логин, пароль, email
* ___Рассказать о мечте___ - форма постинга и редактирования мечты

### Структура БД
#### Транзакции (txs):
##### Структура таблицы
```mysql
CREATE TABLE txs (
  id                        SERIAL PRIMARY KEY,
  created                   BIGINT UNSIGNED NOT NULL,
  scheduled                BIGINT UNSIGNED,
  processed                 BIGINT UNSIGNED,
  `from_type`               INT UNSIGNED NOT NULL,
  `to_type`                 INT UNSIGNED NOT NULL,
  `from_id`                 BIGINT UNSIGNED,
  `to_id`                   BIGINT UNSIGNED,
  `from_route_type`         INT UNSIGNED,
  `to_route_type`           INT UNSIGNED,
  `from_route_id`           BIGINT UNSIGNED,
  `to_route_id`             BIGINT UNSIGNED,
  `from`                    VARCHAR(100),
  `to`                      VARCHAR(100),
  `type`                    INT UNSIGNED NOT NULL,
  `msg`                     VARCHAR(100),
  state                     INT UNSIGNED NOT NULL,
  currency                  INT UNSIGNED NOT NULL,
  amount                    BIGINT UNSIGNED NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```
* _id_
* _created_ - дата создания транзакции
* _scheduled_ - плановая дата обработки. Если дата проставлена, то транзакция должна выполнится не сразу, а после этой даты.
* _processed_ - дата окончательной обработки транзакции. Если транзакция еще не обработатна, то поле равно null. Если транзакция была отменена ставится дата отмены. Если транзакция выполнена, то проставляется дата выполнения.
* _from_type_ - тип отправителя. Поскольку идентификаторы отправителя могут пыть идентификаторам любой сущности, а также отправитель может быть указан в текстовом поле, то нам необходимо задавать тут тип отправителя. В зависимости от этого типа будет тем или иным образом считываться и интерпретироваться адрес из from_id или поля from.
* _to_type_ - тип получателя. Остальное справедливо как и для _from_type_
* _from_id_ -  идетификатор отправителя в случае, если сущность отправителя имеет id в базе данных. Т.е. отправитель либо пользователь, либо пост.
* _to_id_ - идентификатор получателя. См. from_id.
* _from_route_type_ иногда сущность без баланса отправляет средства сущности с балансом, если это поле заполнено, то средства были отправлены сущностью данного типа  
* _to_route_type_ иногда сущность без баланса принимает средства на счет сущности с балансом, если это поле заполнено, то средства были сущности данного типа на баланс владельца сущности
* _from_route_id_ - 
* _to_route_id_ 
* _from_ - адрес отправителя в случае, если это блокчейн адрес (например _from_type_ указан как ETH)
* _to_ - aдрес получателя. См. to
* _type_ - классификатор типа транзакции внтури системы. Своего рода интерпретация транзакции с точик зрения бизнесс-логики системы
* _msg_ - human-readable описание транзакции
* _currency_ - тип валюты
* _amounnt_ - сколько

#### Лайки (likes):
##### Структура таблицы
```mysql
CREATE TABLE likes (
  id                        SERIAL PRIMARY KEY,
  owner_id                  BIGINT UNSIGNED NOT NULL,
  target_type               INT UNSIGNED NOT NULL,
  traget_id                 BIGINT UNSIGNED NOT NULL,
  created                   BIGINT UNSIGNED NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```
* _id_
* _owner_id_ - идентификатор пользователя, который осуществил лайк
* _target_type_ - тип сущности, которую лайкнули
* _target_id_ - идентификатор сущности, которрую лайкнули
* _created_ - дата лайка

#### Посты (posts)
##### Структура таблицы
```mysql
CREATE TABLE posts (
  id                        SERIAL PRIMARY KEY,
  owner_id                  BIGINT UNSIGNED NOT NULL,
  title                     VARCHAR(100) NOT NULL,
  thumbnail                 VARCHAR(100),
  content                   TEXT NOT NULL,
  content_type              INT UNSIGNED NOT NULL,
  post_type                 INT UNSIGNED NOT NULL,
  balance                   BIGINT UNSIGNED,
  `limit`                   BIGINT UNSIGNED,
  status                    INT UNSIGNED NOT NULL,
  promo                     BIGINT UNSIGNED NOT NULL,
  type_status               INT UNSIGNED NOT NULL,
  likes_count               BIGINT UNSIGNED NOT NULL,
  comments_count            BIGINT UNSIGNED NOT NULL,
  created                   BIGINT UNSIGNED NOT NULL,
  views_count               INT UNSIGNED NOT NULL,
  reward_type               INT UNSIGNED NOT NULL,
  reward_token              INT UNSIGNED NOT NULL,
  reward_power              INT UNSIGNED NOT NULL,
  reward_dollar             INT UNSIGNED NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```
* _id_
* _owner_id_ - идентификатор пользователя владельца поста
* _title_ - заголовок мечты
* _thumbnail_ - URL изображения, которое буде отображаться как миниатюра поста. Если урл отсутствует, то берется сначала картинка из поста и, если ее нет, то брендированная картина
* _content_ - содержимое поста, может быть в различной разметке, в зависимости от content_type
* _content_type_ - тип содержимого контента поста. Это может быть различная разметка, например маркдаун или html или обычый текст
* _post_type_ - тип поста - определяется типом сущности
* _balance_ - ...
* _limit_ - предел сбора на мечту в dollars
* _status_ -
* _promo_ - баланс промо сожженных dollar'ов. Когда пользователь продвигает пост, то он сжигает свои dollars в счет этого баланса. По данному балансу посты фильтруются в разделе promo.
* _type_status_ - статус конкретного типа поста
* _likes_count_ - кэшированное количество лайков, чтобы не считать постоянно
* _comments_count_ - кэшированое количество комметариев, чтобы не считать постоянно
* _created_ - дата создания
* _views_count_ - количество просмотров
* _reward_type_ - тип распределения наград (см. типы наград)
* _reward_token_ - сколько получил token автор поста за все время
* _reward_power_ - сколько получил power автор поста за все время
* _reward_dollar_ - сколько получил dollars автор поста за все время

#### Пользователь (users)
##### Структура таблицы
```mysql
CREATE TABLE users (
  id                        SERIAL PRIMARY KEY,
  login                     VARCHAR(100) NOT NULL UNIQUE,
  email                     VARCHAR(100) NOT NULL UNIQUE,
  hash                      VARCHAR(60),
  avatar                    VARCHAR(255),
  background                VARCHAR(255),
  user_status               INT UNSIGNED NOT NULL,
  account_status            INT UNSIGNED NOT NULL,
  balance_token             BIGINT UNSIGNED NOT NULL,
  balance_dollar            BIGINT UNSIGNED NOT NULL,
  balance_power             BIGINT UNSIGNED NOT NULL,
  balance_token_changed     BIGINT UNSIGNED NOT NULL,
  balance_dollar_changed    BIGINT UNSIGNED NOT NULL,
  balance_power_changed     BIGINT UNSIGNED NOT NULL,
  prev_balance_token        BIGINT UNSIGNED NOT NULL,
  prev_balance_dollar         BIGINT UNSIGNED NOT NULL,
  prev_balance_power          BIGINT UNSIGNED NOT NULL,
  prev_balance_token_changed  BIGINT UNSIGNED NOT NULL,
  prev_balance_dollar_changed BIGINT UNSIGNED NOT NULL,
  prev_balance_power_changed  BIGINT UNSIGNED NOT NULL,
  name                      VARCHAR(100),
  surname                   VARCHAR(100),
  platform_eth              VARCHAR(100) UNIQUE,
  timezone_id               INT UNSIGNED NOT NULL,
  registered                BIGINT UNSIGNED NOT NULL,
  confirm_code              VARCHAR(100),
  posts_counter             INT UNSIGNED NOT NULL,
  posts_counter_started     BIGINT UNSIGNED NOT NULL,
  likes_counter             INT UNSIGNED NOT NULL,
  likes_counter_started     BIGINT UNSIGNED NOT NULL,
  comments_counter          INT UNSIGNED NOT NULL,
  comments_counter_started  BIGINT UNSIGNED NOT NULL,
  posts_count               BIGINT UNSIGNED NOT NULL,
  user_type                 INT UNSIGNED NOT NULL,
  phone_number              VARCHAR(100),
  age                       INT UNSIGNED,
  gender                    BOOLEAN,
  education                 VARCHAR(255),
  client_rate               INT UNSIGNED NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```
* _id_ 
* _login_ - логин, отображается везде. Изменить пока нельзя
* _email_ - 
* _hash_ - hash от пароля
* _avatar_ - URL аватарки, если его нет, то берется брендированный
* _user_status_ - статус пользователя (заблокирован, забанен или норомальный)
* _account_status_ - статус аккаунта (подтвержден или нет - жизненный цикл)
* _balance_token_ - баланс TOKEN
* _balance_dollar_ - баланс DOLLARS
* _balance_power_ - баланс POWER
* _balance_token_changed_ - дата последнего изменения баланса TOKEN
* _balance_dollar_changed_ - дата последнего изменения баланса DOLLARS
* _balance_power_changed_ - дата последнего изменения баланса POWER
* _prev_balance_token_ - сохраненное состояние баланс TOKEN
* _prev_balance_dollar_ - сохраненное состояние баланс DOLLARS
* _prev_balance_power_ - сохраненное состояние баланс POWER
* _prev_balance_token_changed_ - дата последнего изменения сохраненного состояни баланса TOKEN
* _prev_balance_dollar_changed_ - дата последнего изменения сохраненного состояни DOLLARS
* _prev_balance_power_changed_ - дата последнего изменения сохраненного состояни POWER
* _name_ - имя пользователя
* _surname_ - фамилия пользовталея
* _eth_address_ - TODO: уникальный адресс, закрепленный за пользователем платформой
* _timezone_id_ - TODO: временная зона
* _registered_ - когда зарегистрирован
* _confirm_code_ - TODO: код пдтверждениня 
* _posts_counter_ - счетчик постов для определения певышения лимита
* _posts_counter_started_ - когда начат отсчет счетчика постов для определения певышения лимита
* _likes_counter_ - счетчик лайков для определения превышения лимита
* _likes_counter_started_ - когда начат отсчет счетчика лайков для определения превышения лимита
* _comments_counter_ - счетчик комментариев для определения превышения лимита
* _comments_counter_started_ - когда начат отсчет счетчика комментариев для определения превышения лимита
* _posts_count_ - суммарное количество опубликованных постов

#### Сессия (sessions)
##### Структура таблицы
```mysql
CREATE TABLE sessions (
  id                        SERIAL PRIMARY KEY,
  user_id                   BIGINT UNSIGNED NOT NULL,
  ip                        VARCHAR(100) NOT NULL, 
  session_key               VARCHAR(100) NOT NULL UNIQUE,
  created                   BIGINT UNSIGNED NOT NULL,
  expire                    BIGINT UNSIGNED NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```
* _id_ 
* _user_id_ - пользователь, кому принадлежит сессия
* _ip_ - ip адресс с которого заходит пользователь
* _session_key_ - 
* _created_ - дата создания сессия
* _exepire_ - дата истечения сессии, если сессию закрыли раньше, то дата обновляется

#### Комментарии (comments)
##### Струкутра таблицы
```mysql
CREATE TABLE comments (
  id                        SERIAL PRIMARY KEY,
  post_id                   BIGINT UNSIGNED NOT NULL,
  owner_id                  BIGINT UNSIGNED NOT NULL,
  parent_id                 BIGINT UNSIGNED,
  content                   TEXT NOT NULL,
  content_type              INT UNSIGNED NOT NULL,
  created                   BIGINT UNSIGNED NOT NULL,
  likes_count               INT UNSIGNED NOT NULL,
  reward_token              BIGINT UNSIGNED NOT NULL,
  reward_power              BIGINT UNSIGNED NOT NULL,
  reward_dollar             BIGINT UNSIGNED NOT NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```
* _id_
* _post_id_ - идентификаторо поста, к которому был оставлен комментарий 
* _owner_id - идентификатор создателя комментария
* _parent_id - идентификатор родительского комментария
* _content_ - содержимое комментария 
* _content_type_ - тип содержимого комментария (так же как и в посте)
* _created_ - дата создания
* _likes_count_ - количество лайков, поставленных на комментарий 

#### Роли (roles)
##### Структура таблицы
```mysql
CREATE TABLE roles (
  user_id                   BIGINT UNSIGNED NOT NULL,
  role                      INT USIGNED NOT NULL
  PRIMARY KEY (user_id, role)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```
* _user_id_ - какому пользователю назначена роль
* _role_ - тип роли

#### Тэги (tags, tags_in_posts)
##### Структуры таблиц
```mysql
CREATE TABLE tags (
  id                        SERIAL PRIMARY KEY,
  name                      VARCHAR(100) NOT NULL UNIQUE
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

CREATE TABLE tags_to_posts (
  post_id                   BIGINT UNSIGNED NOT NULL,
  tag_id                    BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (post_id, tag_id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

#### Энумераторы 

###### Статсы транзакций
* _APPROVED_
* _PROCESSED_

###### Роли
* _CLIENT_
* _ADMIN_

###### Статусы пользователя
* _NORMAL_
* _LOCKED_

###### Статусы аккаунта (жизненный цикл)
* _CONFIRMED_
* _WAITE_CONFIRMATION_

###### Статус поста
* _ACTIVE_

###### Статус типа поста - 
* _ACTIVE_

###### Типы контента
* _MARKDOWN_
* _HTML_
* _TEXT_

###### Типы валюты
* _TOKEN_ 
* _POWER_
* _DOLLAR_
* _ETH_

###### Типы транзакций внутри системы
Описывает тип транзакций в терминах бизнес-логики системы. Тип транзакции в терминах технической маршрутизации можно посмотреть по типам сущностей отправления/получения 
* _USER_TO_USER_ - отправка средств от пользователя к пользователю 
* _LIKER_REWARD_ - награда тому, кто лайкнул
* _LIKE_REWARD_ - награда автору сущности, которую лайкнули
* _COMMENTER_REWARD_ - награда комментатору
* _PROMOTE_POST_ - операция продвижения поста
* _POSTER_REWARD_ - операция вознаграждения за созданиие поста

###### Типы сущностей
* _SYSTEM_ - средства генерируются системой или уничтожаются системой
* _USER_ 
* _REVIEW_ 
* _ARTICLE_
* _COMMENT_
* _ETH_ - средства отправляются или списываются со счета в эфире. В поле from или to указывается адреса эфира. from_id и to_id скорее всего null.

###### Типы наград
* _50_POWER_50_DOLLARS_ - вознаграждение определяеся как 50/50 power/dollars
* _POWER_ - вознаграждение выплачивается как Power
