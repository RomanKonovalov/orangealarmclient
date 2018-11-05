package com.romif.securityalarm.androidclient.feature.service;

import android.net.Uri;
import android.util.Log;

import com.romif.securityalarm.androidclient.feature.dto.UnitDto;
import com.wialon.core.Errors;
import com.wialon.core.Session;
import com.wialon.extra.SearchSpec;
import com.wialon.item.Item;
import com.wialon.item.Resource;
import com.wialon.item.Unit;
import com.wialon.messages.UnitData.Position;
import com.wialon.remote.RemoteHttpClient;
import com.wialon.remote.handlers.ResponseHandler;
import com.wialon.remote.handlers.SearchResponseHandler;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPostHC4;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class WialonService {

    private static final String TAG = "WialonService";
    private static Session session;

    public static CompletableFuture<String> login(String baseUrl, String login, String password) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<NameValuePair> nameValuePairs = new ArrayList<>();
                nameValuePairs.add(new BasicNameValuePair("access_type", "-1"));
                nameValuePairs.add(new BasicNameValuePair("response_type", "token"));
                nameValuePairs.add(new BasicNameValuePair("activation_time", "0"));
                nameValuePairs.add(new BasicNameValuePair("duration", "60"));
                nameValuePairs.add(new BasicNameValuePair("flags", "7"));
                nameValuePairs.add(new BasicNameValuePair("login", login));
                nameValuePairs.add(new BasicNameValuePair("passw", password));
                nameValuePairs.add(new BasicNameValuePair("client_id", "myapp"));

                CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                HttpPostHC4 request = new HttpPostHC4(baseUrl + "/oauth/authorize.html");
                UrlEncodedFormEntityHC4 httpEntity = new UrlEncodedFormEntityHC4(nameValuePairs);
                request.setEntity(httpEntity);

                CloseableHttpResponse response = httpClient.execute(request);
                String location = response.getHeaders("location")[0].getValue();
                Uri uri = Uri.parse(location);
                String accessToken = uri.getQueryParameter("access_token");
                String userName = uri.getQueryParameter("user_name");
                if (accessToken == null || accessToken.isEmpty()) {
                    throw new InvalidCredentialsException();
                }
                return accessToken;
            } catch (IOException e) {
                Log.e(TAG, "Error while getting token", e);
                throw new RuntimeException(e);
            }
        }).thenCompose(token -> loginWithToken(baseUrl, token));

    }

    private static CompletableFuture<String> loginWithToken(String baseUrl, String token) {
        CompletableFuture<String> future = new CompletableFuture<>();
        // initialize Wialon session
        session = Session.getInstance();
        session.initSession(baseUrl);
        // trying login
        session.loginToken(token, new ResponseHandler() {
            @Override
            public void onSuccess(String response) {
                super.onSuccess(response);
                // login succeed
                Log.i(TAG, String.format("Logged successfully. User name is %s", session.getCurrUser().getName()));
                future.complete(session.getCurrUser().getName());
            }

            @Override
            public void onFailure(int errorCode, Throwable throwableError) {
                super.onFailure(errorCode, throwableError);
                // login failed, print error
                Log.e(TAG, Errors.getErrorText(errorCode));
                if (errorCode == 8) {
                    future.completeExceptionally(new InvalidCredentialsException());
                } else {
                    future.completeExceptionally(throwableError != null ? throwableError : new RuntimeException(Errors.getErrorText(errorCode)));
                }
            }
        });
        return future;
    }

    private static CompletableFuture<ArrayList<UnitDto>> getUnits() {
        CompletableFuture<ArrayList<UnitDto>> future = new CompletableFuture<>();
        //Create new search specification
        SearchSpec searchSpec = new SearchSpec();
        //Set items type to search avl_units
        searchSpec.setItemsType(Item.ItemType.avl_unit);
        //Set property name to search
        searchSpec.setPropName("sys_name");
        //Set property value mask to search all units
        searchSpec.setPropValueMask("*");
        //Set sort type by units name
        searchSpec.setSortType("sys_name");
        //Send search by created search specification with items base data flag and from 0 to maximum number
        session.searchItems(searchSpec, 1, Item.dataFlag.base.getValue() | Unit.dataFlag.lastMessage.getValue(), 0, Integer.MAX_VALUE, new SearchResponseHandler() {
            @Override
            public void onSuccessSearch(Item... items) {
                super.onSuccessSearch(items);
                // Search succeed
                Log.i(TAG, "Search items is successful");
                future.complete(new ArrayList<>(Arrays.stream(items).map(item -> new UnitDto((Unit) item)).collect(Collectors.toList())));
            }

            @Override
            public void onFailure(int errorCode, Throwable throwableError) {
                super.onFailure(errorCode, throwableError);
                // search item failed, print error
                Log.e(TAG, Errors.getErrorText(errorCode));
                throw new RuntimeException("Error while getting Units");
            }
        });

        return future;
    }

    public static CompletableFuture<ArrayList<UnitDto>> getUnitDtos(String notificationName, long unitId) {
        AtomicBoolean alarmEnabled = new AtomicBoolean(false);
        return getNotification()
                .thenAccept(notification -> {
                    alarmEnabled.set(!notification.getUnf().entrySet().stream()
                            .filter(e -> notificationName.equals(e.getValue().getN()) && e.getValue().getUn().contains(unitId))
                            .map(entry -> entry.getValue().getFl() == 2)
                            .findFirst()
                            .orElse(false));
                })
                .thenCompose(aVoid -> getUnits())
                .thenApply(unitDtos -> {
                    unitDtos.stream()
                            .filter(u -> u.getId() == unitId)
                            .findFirst()
                            .ifPresent(unitDto -> unitDto.setAlarmEnabled(alarmEnabled.get()));
                    return unitDtos;
                });
    }

    public static CompletableFuture<Position> getLocation(long unitId) {
        CompletableFuture<Position> future = new CompletableFuture<>();

        if (unitId == 0) {
            future.completeExceptionally(new IllegalArgumentException("UnitId should not be null"));
        }

        session.searchItem(unitId, Item.dataFlag.base.getValue() | Unit.dataFlag.lastMessage.getValue(), new SearchResponseHandler() {
            @Override
            public void onSuccessSearch(Item... items) {
                super.onSuccessSearch(items);
                if (items != null && items.length > 0 && (items[0] instanceof Unit) && ((Unit) items[0]).getPosition() != null) {
                    Position unitPosition = ((Unit) items[0]).getPosition();
                    future.complete(unitPosition);
                } else {
                    future.completeExceptionally(new RuntimeException("Error while getting Unit, item does not have position"));
                }
            }

            @Override
            public void onFailure(int errorCode, Throwable throwableError) {
                super.onFailure(errorCode, throwableError);
                Log.e(TAG, Errors.getErrorText(errorCode));
                future.completeExceptionally(new RuntimeException("Error while getting Unit"));
            }
        });

        return future;
    }

    public static CompletableFuture<Resource> getGeozone(boolean includeNotification) {
        CompletableFuture<Resource> future = new CompletableFuture<>();

        SearchSpec searchSpec = new SearchSpec();
        //Set items type to search avl_units
        searchSpec.setItemsType(Item.ItemType.avl_resource);
        //Set property name to search
        searchSpec.setPropName(includeNotification ? "zones_library|notifications" : "zones_library");
        //Set property value mask to search all units
        searchSpec.setPropValueMask("*");
        //Set sort type by units name
        searchSpec.setSortType("zones_library");
        searchSpec.setPropType("propitemname");
        //Send search by created search specification with items base data flag and from 0 to maximum number
        long dataFlags = includeNotification ? Item.dataFlag.base.getValue() | Resource.dataFlag.zones.getValue() | Resource.dataFlag.notifications.getValue() : Item.dataFlag.base.getValue() | Resource.dataFlag.zones.getValue();
        session.searchItems(searchSpec, 1, dataFlags, 0, 1, new SearchResponseHandler() {
            @Override
            public void onSuccessSearch(Item... items) {
                super.onSuccessSearch(items);
                if (items != null && items.length > 0 && (items[0] instanceof Resource)) {
                    Log.i(TAG, "Search Resource is successful");
                    future.complete((Resource) items[0]);
                } else {
                    future.completeExceptionally(new RuntimeException("Error while getting Resource: no resources found"));
                }
            }

            @Override
            public void onFailure(int errorCode, Throwable throwableError) {
                super.onFailure(errorCode, throwableError);
                Log.e(TAG, Errors.getErrorText(errorCode));
                future.completeExceptionally(new RuntimeException("Error while getting Resource", throwableError));
            }
        });

        return future;
    }

    public static CompletableFuture<Boolean> createGeozone(Resource resource, Position position, String geozoneName, int geozoneRadius, int geozoneColor) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        String params = "{\"n\":\"" + geozoneName + "\"," +
                "\"t\":3," +
                "\"w\":" + geozoneRadius + "," +
                "\"f\":0," +
                "\"c\":" + geozoneColor + "," +
                "\"p\":[{\"x\":" + position.getLongitude() + ",\"y\":" + position.getLatitude() + ",\"r\":" + geozoneRadius + "}]," +
                "\"itemId\":" + resource.getId() + "," +
                "\"id\":0," +
                "\"callMode\":\"create\"}";
        RemoteHttpClient.getInstance().remoteCall("resource/update_zone", params, new SearchResponseHandler() {
            @Override
            public void onSuccess(String response) {
                super.onSuccess(response);
                future.complete(true);
            }

            @Override
            public void onFailure(int errorCode, Throwable throwableError) {
                Log.e(TAG, Errors.getErrorText(errorCode));
                super.onFailure(errorCode, throwableError);
                future.completeExceptionally(new RuntimeException("Error while creating Geozone"));
            }
        });
        return future;
    }

    public static CompletableFuture<Boolean> updateGeozone(Resource resource, Position position, String geozoneName, int geozoneRadius, int geozoneColor) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        String zoneId = resource.getZl().entrySet().stream()
                .filter(e -> geozoneName.equals(e.getValue().getN()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);

        String params = "{\"n\":\"" + geozoneName + "\"," +
                "\"t\":3," +
                "\"w\":" + geozoneRadius + "," +
                "\"f\":0," +
                "\"c\":" + geozoneColor + "," +
                "\"p\":[{\"x\":" + position.getLongitude() + ",\"y\":" + position.getLatitude() + ",\"r\":" + geozoneRadius + "}]," +
                "\"itemId\":" + resource.getId() + "," +
                "\"id\":" + zoneId + "," +
                "\"callMode\":\"update\"}";
        RemoteHttpClient.getInstance().remoteCall("resource/update_zone", params, new SearchResponseHandler() {
            @Override
            public void onSuccess(String response) {
                super.onSuccess(response);
                future.complete(true);
            }

            @Override
            public void onFailure(int errorCode, Throwable throwableError) {
                Log.e(TAG, Errors.getErrorText(errorCode));
                super.onFailure(errorCode, throwableError);
                future.completeExceptionally(new RuntimeException("Error while updating Geozone"));
            }
        });
        return future;
    }

    public static CompletableFuture<Resource> getNotification() {
        CompletableFuture<Resource> future = new CompletableFuture<>();

        SearchSpec searchSpec = new SearchSpec();
        //Set items type to search avl_units
        searchSpec.setItemsType(Item.ItemType.avl_resource);
        //Set property name to search
        searchSpec.setPropName("notifications");
        //Set property value mask to search all units
        searchSpec.setPropValueMask("*");
        //Set sort type by units name
        searchSpec.setSortType("notifications");
        //searchSpec.setPropType("propitemname");
        //Send search by created search specification with items base data flag and from 0 to maximum number
        session.searchItems(searchSpec, 1, Item.dataFlag.base.getValue() | Resource.dataFlag.notifications.getValue(), 0, 1, new SearchResponseHandler() {
            @Override
            public void onSuccessSearch(Item... items) {
                super.onSuccessSearch(items);
                if (items != null && items.length > 0 && (items[0] instanceof Resource)) {
                    Log.i(TAG, "Search Resource is successful");
                    future.complete((Resource) items[0]);
                } else {
                    future.complete(null);
                }
            }

            @Override
            public void onSuccess(String response) {
                super.onSuccess(response);
            }

            @Override
            public void onFailure(int errorCode, Throwable throwableError) {
                super.onFailure(errorCode, throwableError);
                Log.e(TAG, Errors.getErrorText(errorCode));
                future.completeExceptionally(new RuntimeException("Error while getting Notification"));
            }
        });

        return future;
    }

    public static CompletableFuture<Boolean> createNotification(Resource resource, long unitId, String email, String geozoneName, String notificationName, String notificationEmailSubject, String notificationPatternText) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        String zoneId = resource.getZl().entrySet().stream()
                .filter(e -> geozoneName.equals(e.getValue().getN()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);

        if (zoneId == null) {
            future.completeExceptionally(new IllegalArgumentException("Geozone with name = " + geozoneName + " not found"));
            return future;
        }

        if (unitId == 0) {
            future.completeExceptionally(new IllegalArgumentException("UnitId not specified"));
            return future;
        }
        if (email.equals("")) {
            future.completeExceptionally(new IllegalArgumentException("Email not specified"));
            return future;
        }

        String params = "{\"n\":\"" + notificationName + "\"," +
                "\"ta\":" + Instant.now().getEpochSecond() + "," +
                "\"td\":" + Instant.now().plus(365, ChronoUnit.DAYS).getEpochSecond() + "," +
                "\"tz\":134228528," +
                "\"la\":\"ru\"," +
                "\"ma\":0," +
                "\"sch\":{\"f1\":0,\"f2\":0,\"t1\":0,\"t2\":0,\"m\":0,\"y\":0,\"w\":0}," +
                "\"un\":[" + unitId + "]," +
                "\"trg\":{\"t\":\"geozone\",\"p\":{\"geozone_ids\":" + zoneId + ",\"geozone_id\":" + zoneId + ",\"type\":1,\"min_speed\":0,\"max_speed\":0,\"sensor_type\":\"\",\"sensor_name_mask\":\"\",\"lower_bound\":0,\"upper_bound\":0,\"merge\":0,\"reversed\":0}}," +
                "\"act\":[{\"t\":\"email\",\"p\":{\"email_to\":\"" + email + "\",\"subj\":\"" + notificationEmailSubject + "\",\"html\":0,\"img_attach\":0}}]," +
                "\"txt\":\"" + notificationPatternText + "\"," +
                "\"fl\":0," +
                "\"mast\":0," +
                "\"mpst\":0," +
                "\"cdt\":0," +
                "\"mmtd\":3600," +
                "\"cp\":3600," +
                "\"id\":0," +
                "\"itemId\":" + resource.getId() + "," +
                "\"callMode\":\"create\"}";
        RemoteHttpClient.getInstance().remoteCall("resource/update_notification", params, new SearchResponseHandler() {
            @Override
            public void onSuccess(String response) {
                super.onSuccess(response);
                future.complete(true);
            }

            @Override
            public void onFailure(int errorCode, Throwable throwableError) {
                Log.e(TAG, Errors.getErrorText(errorCode));
                super.onFailure(errorCode, throwableError);
                future.completeExceptionally(new RuntimeException("Error while creating Notification"));
            }
        });

        return future;
    }

    public static CompletableFuture<Boolean> updateNotification(Resource notification, boolean stop, String notificationName) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        String notificationId = notification.getUnf().entrySet().stream()
                .filter(e -> notificationName.equals(e.getValue().getN()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);

        if (notificationId == null && stop) {
            future.complete(true);
            return future;
        } else if (notificationId == null) {
            future.completeExceptionally(new IllegalArgumentException("Notification with name: " + notificationName + " does not exist"));
            return future;
        }

        String params = "{\"id\":" + notificationId + "," +
                "\"e\":" + (stop ? 0 : 1) + "," +
                "\"itemId\":" + notification.getId() + "," +
                "\"callMode\":\"enable\"}";

        RemoteHttpClient.getInstance().remoteCall("resource/update_notification", params, new SearchResponseHandler() {
            @Override
            public void onSuccess(String response) {
                super.onSuccess(response);
                future.complete(true);
            }

            @Override
            public void onFailure(int errorCode, Throwable throwableError) {
                Log.e(TAG, Errors.getErrorText(errorCode));
                super.onFailure(errorCode, throwableError);
                future.completeExceptionally(new RuntimeException("Error while updating Notification"));
            }
        });

        return future;
    }

    public static CompletableFuture<String> logout() {
        CompletableFuture<String> future = new CompletableFuture<>();
        session.logout(new ResponseHandler() {
            @Override
            public void onSuccess(String response) {
                super.onSuccess(response);
                // logout succeed
                Log.i(TAG, "Logout successfully");
                future.complete(response);
            }

            @Override
            public void onFailure(int errorCode, Throwable throwableError) {
                super.onFailure(errorCode, throwableError);
                // logout failed, print error
                Log.e(TAG, Errors.getErrorText(errorCode));
                future.completeExceptionally(new RuntimeException("Error while logging out: " + Errors.getErrorText(errorCode)));
            }
        });
        return future;
    }

    public static class InvalidCredentialsException extends RuntimeException {

    }

}

/*Session.getInstance().initSession(properties.getProperty("wialon.host"));
            Session.getInstance().login(credential.getId(), credential.getPassword(), new ResponseHandler() {
                @Override
                public void onSuccess(String response) {
                    super.onSuccess(response);

                    *//*UpdateSpec updateSpec = new UpdateSpec();
                    updateSpec.setMode(1);
                    updateSpec.setType("type");
                    updateSpec.setData(Item_.ItemType.avl_unit);
                    updateSpec.setFlags(((Item_.dataFlag.base.getValue() | Unit.dataFlag.lastMessage.getValue()) | Item_.dataFlag.image.getValue()) | Unit.dataFlag.restricted.getValue());
                    Session.getInstance().updateDataFlags(new UpdateSpec[]{updateSpec}, new ResponseHandler() {
                        public void onSuccess(String response) {
                            super.onSuccess(response);
                            ((MainActivity) getActivity()).saveCredential(credential);

                            Collection<Item_> items = Session.getInstance().getItems();
                            for (Item_ item: items) {


                                if (item != null && (item instanceof Unit) && ((Unit) item).getPosition() != null) {
                                    UnitData.Position unitPosition = ((Unit) item).getPosition();
                                    if (getActivity() != null) {
                                        unitPosition.getLatitude();
                                    }
                                }
                            }
                        }

                        public void onFailure(final int errorCode, Throwable throwableError) {
                            super.onFailure(errorCode, throwableError);
                            new AsyncTask<Void, Void, Void>() {
                                @Override
                                protected Void doInBackground(Void... voids) {
                                    Log.d(TAG, "Credentials are invalid. Username or password are " +
                                            "incorrect.");
                                    Toast.makeText(view.getContext(), R.string.invalid_creds_toast_msg,
                                            Toast.LENGTH_SHORT).show();
                                    setSignEnabled(true);
                                    return null;
                                }
                            };
                        }
                    });*//*

                    UpdateSpec updateSpec1 = new UpdateSpec();
                    updateSpec1.setMode(1);
                    updateSpec1.setType("type");
                    updateSpec1.setData(Item_.ItemType.avl_resource);
                    updateSpec1.setFlags((Item_.dataFlag.base.getValue() | Resource.dataFlag.notifications.getValue()));
                    Session.getInstance().updateDataFlags(new UpdateSpec[]{updateSpec1}, new ResponseHandler() {
                        public void onSuccess(String response) {
                            super.onSuccess(response);

                            Collection<Item_> items = Session.getInstance().getItems();
                            for (Item_ item: items) {


                                if (item != null && (item instanceof Resource) && ((Resource) item).getNotificationPlugin() != null) {
                                    ItemPropertiesData notificationPlugin = ((Resource) item).getNotificationPlugin();
                                    notificationPlugin.getPropertyData(new long[]{0L, 1L, 2L, 3L}, new ResponseHandler() {
                                        @Override
                                        public void onSuccess(String response) {
                                            super.onSuccess(response);
                                            Collection<Resource> resources = Session.getInstance().getItems(Resource.class);
                                            for (Resource resource:  resources) {
                                                resource.getNotificationPlugin().updateProperty("{\"id\":1, \"e\":0}", "enable", new ResponseHandler() {
                                                    @Override
                                                    public void onSuccess(String response) {
                                                        super.onSuccess(response);
                                                    }

                                                    @Override
                                                    public void onFailure(int errorCode, Throwable throwableError) {
                                                        super.onFailure(errorCode, throwableError);
                                                    }
                                                });
                                            }

                                        }

                                        @Override
                                        public void onFailure(int errorCode, Throwable throwableError) {
                                            super.onFailure(errorCode, throwableError);
                                        }
                                    });

                                }
                            }
                        }
                    });









                }

            });*/