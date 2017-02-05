package org.bimserver.servlets;

import java.io.IOException;
import java.net.URL;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.oltu.oauth2.as.issuer.MD5Generator;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.OAuthResponse;
import org.apache.oltu.oauth2.ext.dynamicreg.server.request.JSONHttpServletRequestWrapper;
import org.apache.oltu.oauth2.ext.dynamicreg.server.request.OAuthServerRegistrationRequest;
import org.apache.oltu.oauth2.ext.dynamicreg.server.response.OAuthServerRegistrationResponse;
import org.bimserver.BimServer;
import org.bimserver.BimserverDatabaseException;
import org.bimserver.database.DatabaseSession;
import org.bimserver.models.store.OAuthServer;
import org.bimserver.models.store.StorePackage;
import org.bimserver.shared.exceptions.ServiceException;
import org.bimserver.utils.NetUtils;

public class OAuthRegistrationServlet extends SubServlet {

	public OAuthRegistrationServlet(BimServer bimServer, ServletContext servletContext) {
		super(bimServer, servletContext);
	}

	@Override
	public void service(HttpServletRequest request, HttpServletResponse httpResponse) throws ServletException, IOException {
		OAuthServerRegistrationRequest oauthRequest = null;
        try {
            oauthRequest = new OAuthServerRegistrationRequest(new JSONHttpServletRequestWrapper(request));
            oauthRequest.discover();
            oauthRequest.getClientUrl();
            oauthRequest.getClientDescription();
            oauthRequest.getRedirectURI();

            try (DatabaseSession session = getBimServer().getDatabase().createSession()) {
            	OAuthServer oAuthServer = session.querySingle(StorePackage.eINSTANCE.getOAuthServer_RedirectUrl(), oauthRequest.getRedirectURI());
            	
            	GregorianCalendar now = new GregorianCalendar();
            	if (oAuthServer == null) {
            		oAuthServer = session.create(OAuthServer.class);
            		oAuthServer.setClientName(oauthRequest.getClientName());
            		oAuthServer.setClientUrl(oauthRequest.getClientUrl());
            		oAuthServer.setClientDescription(oauthRequest.getClientDescription());
            		
            		if (oauthRequest.getClientIcon() != null) {
            			byte[] icon = NetUtils.getContentAsBytes(new URL(oauthRequest.getClientIcon()), 5000);
            			oAuthServer.setClientIcon(icon);
            		}
            		oAuthServer.setRedirectUrl(oauthRequest.getRedirectURI());
            		
//            		DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy hh:mm:ss");
            		
            		GregorianCalendar expires = new GregorianCalendar();
            		expires.add(Calendar.YEAR, 1);
            		
            		String secret = new MD5Generator().generateValue();
            		
            		oAuthServer.setIssuedAt(now.getTime());
            		oAuthServer.setExpiresAt(expires.getTime());
            		oAuthServer.setClientSecret(secret);
            		oAuthServer.setClientId(oauthRequest.getClientName().replace(" ", "").toLowerCase());
            		oAuthServer.setIncoming(true);
            		session.commit();
            	}
            	OAuthResponse response = OAuthServerRegistrationResponse
            			.status(HttpServletResponse.SC_OK)
            			.setClientId(oAuthServer.getClientId())
            			.setClientSecret(oAuthServer.getClientSecret())
            			.setIssuedAt("" + oAuthServer.getIssuedAt().getTime())
            			.setExpiresIn(oAuthServer.getExpiresAt().getTime() - now.getTimeInMillis())
            			.setParam("message", "OK")
            			.buildJSONMessage();
            	httpResponse.setStatus(response.getResponseStatus());
            	httpResponse.getWriter().write(response.getBody());
            } catch (BimserverDatabaseException e) {
				e.printStackTrace();
			} catch (ServiceException e) {
				e.printStackTrace();
			}
        } catch (OAuthProblemException e) {
            OAuthResponse response;
			try {
				response = OAuthServerRegistrationResponse
				    .errorResponse(HttpServletResponse.SC_BAD_REQUEST)
				    .error(e)
				    .buildJSONMessage();
				httpResponse.setStatus(response.getResponseStatus());
				httpResponse.getWriter().write(response.getBody());
			} catch (OAuthSystemException e1) {
				e1.printStackTrace();
			}
        } catch (OAuthSystemException e) {
			e.printStackTrace();
		}
	}
}