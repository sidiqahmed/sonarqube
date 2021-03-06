/*
 * SonarQube
 * Copyright (C) 2009-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import * as React from 'react';
import { Helmet } from 'react-helmet';
import EmptyOverview from './EmptyOverview';
import OverviewApp from './OverviewApp';
import SonarCloudEmptyOverview from './SonarCloudEmptyOverview';
import Suggestions from '../../../app/components/embed-docs-modal/Suggestions';
import { isShortLivingBranch } from '../../../helpers/branches';
import {
  getShortLivingBranchUrl,
  getCodeUrl,
  getProjectUrl,
  getBaseUrl,
  getPathUrlAsString
} from '../../../helpers/urls';
import { isSonarCloud } from '../../../helpers/system';
import { withRouter, Router } from '../../../components/hoc/withRouter';

interface Props {
  branchLike?: T.BranchLike;
  branchLikes: T.BranchLike[];
  component: T.Component;
  isInProgress?: boolean;
  isPending?: boolean;
  onComponentChange: (changes: Partial<T.Component>) => void;
  router: Pick<Router, 'replace'>;
}

export class App extends React.PureComponent<Props> {
  componentDidMount() {
    const { branchLike, component } = this.props;

    if (this.isPortfolio()) {
      this.props.router.replace({
        pathname: '/portfolio',
        query: { id: component.key }
      });
    } else if (this.isFile()) {
      this.props.router.replace(
        getCodeUrl(component.breadcrumbs[0].key, branchLike, component.key)
      );
    } else if (isShortLivingBranch(branchLike)) {
      this.props.router.replace(getShortLivingBranchUrl(component.key, branchLike.name));
    }
  }

  isPortfolio = () => ['VW', 'SVW'].includes(this.props.component.qualifier);

  isFile = () => ['FIL', 'UTS'].includes(this.props.component.qualifier);

  render() {
    const { branchLike, branchLikes, component } = this.props;

    if (this.isPortfolio() || this.isFile() || isShortLivingBranch(branchLike)) {
      return null;
    }

    return (
      <>
        {isSonarCloud() && (
          <Helmet>
            <link
              href={getBaseUrl() + getPathUrlAsString(getProjectUrl(component.key))}
              rel="canonical"
            />
          </Helmet>
        )}
        <Suggestions suggestions="overview" />

        {!component.analysisDate &&
          (isSonarCloud() ? (
            <SonarCloudEmptyOverview
              branchLike={branchLike}
              branchLikes={branchLikes}
              component={component}
              hasAnalyses={this.props.isPending || this.props.isInProgress}
              onComponentChange={this.props.onComponentChange}
            />
          ) : (
            <EmptyOverview
              branchLike={branchLike}
              branchLikes={branchLikes}
              component={component.key}
              showWarning={!this.props.isPending && !this.props.isInProgress}
            />
          ))}
        {component.analysisDate && (
          <OverviewApp
            branchLike={branchLike}
            component={component}
            onComponentChange={this.props.onComponentChange}
          />
        )}
      </>
    );
  }
}

export default withRouter(App);
