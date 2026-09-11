# 빛 레기온 미터기 유지

이 저장소는 [TK-open-public/Aion2-Dps-Meter](https://github.com/TK-open-public/Aion2-Dps-Meter) (AION2Meter4J, MIT)의 빛 레기온 포크입니다. 캡처·오버레이는 업스트림을 따라가고, 배포 채널과 전투 스냅샷만 이 포크에서 관리합니다.

## 업스트림 머지

```bash
git remote add upstream https://github.com/TK-open-public/Aion2-Dps-Meter.git   # 한 번만
git fetch upstream
git merge upstream/main
```

충돌이 나면 거의 항상 `src/main/kotlin/packet/StreamProcessor.kt`와 `src/main/resources/json/` 입니다.

- 옵코드/파서 충돌: 업스트림 쪽을 우선하고, 우리 브랜딩·스냅샷 코드는 건드리지 않습니다.
- json 충돌: 아래 카탈로그 갱신으로 다시 뽑는 편이 안전합니다.
- `useVersionCheck.ts`의 Releases URL은 다시 `wildcatXD/Aion2-Dps-Meter`인지 확인합니다.
- `packageName` / 창 제목 / MSI 이름은 업스트림 `aion2meter4j`로 되돌아가지 않게 확인합니다.

업스트림 공개 저장소는 2026-06-19에 종료 안내가 올라갔습니다. 이후 커밋이 없으면 옵코드는 우리가 직접 고쳐야 합니다.

## 옵코드가 깨졌을 때

증상:

- UI는 보이는데 딜이 하나도 안 뜸 → Damage/DoT 옵코드 또는 Npcap 캡처 필터
- 이름이 숫자(엔티티 ID)로만 뜸 → OwnNickname / OtherNickname 옵코드
- 파티에 없는 유령 행, 소환수 딜이 따로 집계 → Summon 옵코드/마커
- 스킬명이 숫자 → `skills.json` 카탈로그가 구버전
- 보스 이름이 코드만 나오거나 보스 판정이 틀림 → `mobs.json`

캡처 기본값: `206.127.156.0/24` 포트 `13328` (`PcapCapturerConfig`). 게임 서버 IP 대역이 바뀌면 설정 프로퍼티도 바꿔야 합니다.

## json 카탈로그 갱신

런타임에 `mobs.json`, `skills.json`, `buff.json`이 없으면 미터기가 기동 시 죽습니다. 이 포크는 `src/main/resources/json/`을 커밋합니다.

1.7.5 MSI에서 다시 꺼내는 예:

```bash
msiextract -C /tmp/meter-msi bit-dps-meter-x.x.x.msi   # 또는 업스트림 aion2meter4j MSI
# app/*.jar 안에서 json/ 디렉터리를 찾아 복사
jar xf aion2meter4j-*.jar json
cp json/* src/main/resources/json/
```

원본 표기는 `src/main/resources/json/NOTICE.md`를 유지합니다. (hate-jp / HappNJLand, MIT)

공개 카탈로그를 그대로 덮어쓰지 않습니다.

- HappNJLand `resources/` 마지막 갱신은 2026-04-08. 우리 1.7.5 MSI(6월)보다 오래됐고, 한글 이름이 아닌 테스트/봇 엔티티가 많습니다. 7월 이후 던전·성역 보스가 없습니다.
- 지금 유지되는 쪽은 [Aion2Cal](https://github.com/zerosial/Aion2Tools) MSI입니다. 1.9.14(2026-07-01)에서 무스펠 보스 ID `2301089`/`2301090`만 추가로 가져왔습니다. 저장소에는 JSON이 없고 MSI 안에만 있습니다.

파티 신청 패널 스킬 목록은 `src/main/resources/src/constants/codes.ts` 입니다. 업스트림은 이 파일을 gitignore에 두었지만, 이 포크는 프론트 빌드가 되도록 커밋합니다.

## 웹 연동

- 패킷 리더는 **이 미터기 저장소에만** 둡니다. `aion2-legion-web`에 넣지 않습니다.
- 길드 웹의 낫터기 ingest (`/api/admin/notmeter/combat`)를 미터기 업로드로 재사용하지 않습니다.
- **알림·일정 참여**는 웹 `/meter-link` 일회용 코드 → 미터기 설정의 기기 토큰으로 붙습니다. 오버레이가 30초마다 `GET /api/meter/inbox`를 보고, 참가/취소/공지 읽음/아그로는 `POST /api/meter/actions`입니다.
- 기기 토큰은 `%APPDATA%\Aion2DpsMeter\settings.properties`의 `guildDeviceToken`에 저장됩니다. 웹에서 기기를 해제하면 오버레이는 401을 받습니다.
- 전투가 끝나면 `DpsLog.encounter`에 `bit-legion-encounter-v1` JSON 스냅샷이 붙습니다. 업로드는 기기 토큰으로 `POST /api/meter/encounters`만 씁니다. 낫터기 ingest(`/api/admin/notmeter/combat`)는 재사용하지 않습니다.
- 스냅샷 플레이어에 `dps`와 실험 `nDps`가 같이 들어 갑니다. 웹 랭킹을 붙일 때 어떤 지표를 쓸지 웹 API에서 고르면 됩니다.

## 캡처 체크리스트 (오드·열쇠·스킬 쿨)

파서를 먼저 만들지 않습니다. 캡처가 오면 `StreamProcessor`에만 옵코드를 넣고, 이미 있는 `DataManager.odeEnergy` / `shugoKeys` / 추적 오버레이에 숫자를 연결합니다.

| 상황 | 남길 것 |
| --- | --- |
| 오드에너지 변동 | 사용·충전 시각, 변하기 전/후 숫자, `app.log` 해당 구간 |
| 슈고페스타 열쇠 | 획득·사용·인벤 갯수가 보일 때 |
| 스킬 재사용 대기 | 시전 직후 쿨이 뜨는 구간. 버프 지속시간 패킷(`0x2A/0x2B 0x38`)과 구분 |

캡처 기본값: `206.127.156.0/24` 포트 `13328`. 디버그 모드에서 미확인 패킷 hex가 `app.log`에 남습니다.

## 이 환경에서 못 하는 일

Cloud Agent는 Linux입니다. Npcap, JavaFX Windows 모듈, MSI 패키징, 실제 오버레이 확인은 Windows에서 합니다.
